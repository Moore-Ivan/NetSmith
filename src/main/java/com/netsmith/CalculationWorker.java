package com.netsmith;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CalculationWorker {

    /** 进度对话框延迟显示毫秒数：短于此耗时的任务完全不弹窗，避免快速操作"闪窗" */
    private static final int DIALOG_SHOW_DELAY_MS = 300;

    private static final List<SwingWorker<?, ?>> activeWorkers = new CopyOnWriteArrayList<>();
    // 使用 WeakHashMap 避免内存泄漏，Worker 被 GC 时自动清理；
    // 该映射会被多个 worker 线程（put）和 EDT（get/remove）并发访问，必须包装为同步 Map
    private static final Map<SwingWorker<?, ?>, String> workerMessages =
            Collections.synchronizedMap(new WeakHashMap<>());

    public interface CalculationCallback<T> {
        default void onStart() {}
        void onProgress(int progress, String message);
        void onComplete(T result);
        void onError(Exception e);
    }

    public static <T> void execute(CallableWithProgress<T> task, CalculationCallback<T> callback) {
        final SwingWorker<T, Object> worker = new SwingWorker<T, Object>() {
            @Override
            protected T doInBackground() throws Exception {
                task.setProgressHandler((progress, message) -> {
                    if (!isCancelled()) {
                        setProgress(progress);
                        workerMessages.put(this, message != null ? message : "");
                    }
                });

                // 在调用任务前检查是否已取消
                if (isCancelled()) {
                    throw new java.util.concurrent.CancellationException("Task cancelled");
                }

                return task.call();
            }

            @Override
            protected void done() {
                activeWorkers.remove(this);
                workerMessages.remove(this);
                if (isCancelled()) {
                    return;
                }
                try {
                    T result = get();
                    if (callback != null) {
                        callback.onComplete(result);
                    }
                } catch (java.util.concurrent.CancellationException e) {
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    // 检测是否为取消操作引起的异常
                    if (cause instanceof RuntimeException && 
                        (cause.getMessage() != null && cause.getMessage().contains("已取消"))) {
                        // 用户取消操作，静默处理
                        return;
                    }
                    if (callback != null) {
                        callback.onError(cause instanceof Exception ? (Exception) cause : e);
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onError(e);
                    }
                }
            }
        };

        if (callback != null) {
            worker.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("progress".equals(evt.getPropertyName())) {
                        int progress = (Integer) evt.getNewValue();
                        String message = workerMessages.getOrDefault(worker, "");
                        callback.onProgress(progress, message);
                    }
                }
            });
        }

        activeWorkers.add(worker);
        worker.execute();
    }

    public static void cancelAllTasks() {
        for (SwingWorker<?, ?> worker : activeWorkers) {
            if (!worker.isDone()) {
                worker.cancel(true);
            }
        }
        activeWorkers.clear();
        workerMessages.clear();
    }

    public static int getActiveTaskCount() {
        return activeWorkers.size();
    }

    public interface CallableWithProgress<T> {
        void setProgressHandler(ProgressHandler handler);
        T call() throws Exception;
    }

    @FunctionalInterface
    public interface ProgressHandler {
        void onProgress(int progress, String message);
    }

    public static <T> void executeWithProgress(Component parent, String title,
            CallableWithProgress<T> task, CalculationCallback<T> callback) {
        executeWithProgress(parent, title, null, task, callback);
    }

    /**
     * 带进度对话框的后台执行。
     * 体验优化：
     * 1. 对话框延迟 {@value #DIALOG_SHOW_DELAY_MS} 毫秒才显示——快速计算在延迟内完成时完全不弹窗，
     *    避免"闪窗"造成的视觉干扰；只有真正的长任务才展示加载状态。
     * 2. 传入 actionSource（触发按钮）时，任务期间禁用该按钮，防止用户重复点击发起重复任务。
     * 3. 支持 ESC 快捷键取消任务（与"取消"按钮等价）。
     */
    public static <T> void executeWithProgress(Component parent, String title, JComponent actionSource,
            CallableWithProgress<T> task, CalculationCallback<T> callback) {

        Window parentWindow = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(parentWindow, title, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setSize(320, 140);
        dialog.setLocationRelativeTo(parent);
        dialog.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);

        JLabel messageLabel = new JLabel("正在计算...");

        JButton cancelBtn = new JButton("取消");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(cancelBtn);

        panel.add(messageLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.pack();

        final SwingWorker<T, ?>[] workerRef = new SwingWorker[1];
        // 延迟显示定时器（数组形式供内部类引用），任务结束时统一停止
        final javax.swing.Timer[] showTimer = new javax.swing.Timer[1];

        CalculationCallback<T> wrappedCallback = new CalculationCallback<T>() {
            @Override
            public void onProgress(int progress, String message) {
                if (messageLabel != null) {
                    messageLabel.setText(message);
                }
                if (progressBar != null && progress >= 0 && progress <= 100) {
                    progressBar.setValue(progress);
                }
                if (callback != null) {
                    callback.onProgress(progress, message);
                }
            }

            @Override
            public void onComplete(T result) {
                finishProgress(showTimer[0], dialog, actionSource);
                if (callback != null) {
                    callback.onComplete(result);
                }
            }

            @Override
            public void onError(Exception e) {
                finishProgress(showTimer[0], dialog, actionSource);
                if (callback != null) {
                    callback.onError(e);
                }
            }
        };

        cancelBtn.addActionListener(e -> {
            if (workerRef[0] != null) {
                workerRef[0].cancel(true);
            }
            finishProgress(showTimer[0], dialog, actionSource);
        });

        // ESC 取消任务（与点击"取消"按钮等价），窗口关闭按钮保持禁用，避免误关
        dialog.getRootPane().registerKeyboardAction(
                ev -> cancelBtn.doClick(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        final SwingWorker<T, Object> worker = new SwingWorker<T, Object>() {
            @Override
            protected T doInBackground() throws Exception {
                task.setProgressHandler((progress, message) -> {
                    if (!isCancelled()) {
                        setProgress(progress);
                        workerMessages.put(this, message != null ? message : "");
                    }
                });

                // 在调用任务前检查是否已取消
                if (isCancelled()) {
                    throw new java.util.concurrent.CancellationException("Task cancelled");
                }

                return task.call();
            }

            @Override
            protected void done() {
                activeWorkers.remove(this);
                workerMessages.remove(this);
                if (isCancelled()) {
                    finishProgress(showTimer[0], dialog, actionSource);
                    return;
                }
                try {
                    T result = get();
                    wrappedCallback.onComplete(result);
                } catch (java.util.concurrent.CancellationException e) {
                    finishProgress(showTimer[0], dialog, actionSource);
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    // 检测是否为取消操作引起的异常
                    if (cause instanceof RuntimeException &&
                        (cause.getMessage() != null && cause.getMessage().contains("已取消"))) {
                        // 用户取消操作，静默关闭对话框
                        finishProgress(showTimer[0], dialog, actionSource);
                        return;
                    }
                    wrappedCallback.onError(cause instanceof Exception ? (Exception) cause : e);
                } catch (Exception e) {
                    wrappedCallback.onError(e);
                }
            }
        };

        worker.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("progress".equals(evt.getPropertyName())) {
                    int progress = (Integer) evt.getNewValue();
                    String message = workerMessages.getOrDefault(worker, "");
                    wrappedCallback.onProgress(progress, message);
                }
            }
        });

        if (callback != null) {
            callback.onStart();
        }
        workerRef[0] = worker;
        activeWorkers.add(worker);
        // 任务期间禁用触发按钮，防止用户重复点击发起重复任务；结束后由 finishProgress 恢复
        if (actionSource != null) {
            actionSource.setEnabled(false);
        }
        worker.execute();

        // 延迟显示：任务在 DIALOG_SHOW_DELAY_MS 内完成则完全不弹窗；超时未完成才展示加载状态
        javax.swing.Timer timer = new javax.swing.Timer(DIALOG_SHOW_DELAY_MS, ev -> {
            if (!worker.isDone()) {
                dialog.setVisible(true);
            }
        });
        timer.setRepeats(false);
        showTimer[0] = timer;
        timer.start();
    }

    /** 任务结束统一收尾：停止延迟显示定时器、关闭对话框、恢复触发按钮可用状态 */
    private static void finishProgress(javax.swing.Timer showTimer, JDialog dialog, JComponent actionSource) {
        if (showTimer != null) {
            showTimer.stop();
        }
        dialog.dispose();
        if (actionSource != null) {
            actionSource.setEnabled(true);
        }
    }
}