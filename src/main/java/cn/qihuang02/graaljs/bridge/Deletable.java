package cn.qihuang02.graaljs.bridge;

/**
 * 实现此接口的对象在被 JS 侧删除时（如 {@code delete obj.prop} 或从 List 中移除）
 * 会收到通知回调。
 *
 * <pre>{@code
 * public class ManagedResource implements Deletable {
 *     @Override
 *     public void onDeletedByJS() {
 *         // 清理资源
 *     }
 * }
 * }</pre>
 */
public interface Deletable {
    /**
     * 当此对象被 JS 侧删除时调用。
     */
    void onDeletedByJS();

    /**
     * 如果对象实现了 {@link Deletable}，调用其 {@link #onDeletedByJS()} 方法。
     *
     * @param object 可能实现了 Deletable 的对象
     */
    static void deleteObject(Object object) {
        if (object instanceof Deletable deletable) {
            deletable.onDeletedByJS();
        }
    }
}
