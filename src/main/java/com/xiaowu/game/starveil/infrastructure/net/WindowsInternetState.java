package com.xiaowu.game.starveil.infrastructure.net;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.ShortByReference;
import com.xiaowu.game.starveil.platform.common.SystemDetector;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * Windows 的联网状态（网络列表管理器 NLM）—— <b>就是任务栏那个「无 Internet」图标的数据源</b>。
 *
 * <h2>为什么比 TCP 探测更合适</h2>
 * 自己发 TCP 探测有两个毛病：一是要发网络包（所以不能问得太勤），
 * 二是它只能告诉你「我连得上某个地址」，对「有网络但没互联网」这种情况
 * （连着路由器、路由器没连上外网）只能靠探测失败来推断。
 *
 * <p>Windows 自己维护着这件事：NCSI 会去探微软的连通性检测地址，
 * 结论通过 {@code INetworkListManager} 对外暴露为
 * {@code IsConnectedToInternet()} / {@code GetConnectivity()}，
 * 也正是任务栏「有网 / 无 Internet」图标的依据。
 * 这是个<b>本地 COM 调用</b>：不发网络包、开销是微秒级，
 * 所以可以问得比 TCP 探测勤得多，也就能第一时间发现「有网络但没互联网」。
 *
 * <h2>可靠性说明</h2>
 * 它是「Windows 的判定」，不是绝对真理：
 * <ul>
 *   <li>NCSI 自己有探测周期，切换瞬间可能有一小段滞后；</li>
 *   <li>企业代理 / 强制门户下 NCSI 可能判定为「无 Internet」，而玩家的游戏其实能连通。</li>
 * </ul>
 * 所以框架把它当作<b>首选信号</b>，拿不到时退回 TCP 探测
 * （见 {@link NetworkStatus}）—— 两条路都有，才不会因为一边不准而整体失灵。
 */
public final class WindowsInternetState {

    /** CLSID_NetworkListManager */
    private static final Guid.CLSID CLSID_NETWORK_LIST_MANAGER =
            new Guid.CLSID("DCB00C01-570F-4A9B-8D69-199FDBA5723B");
    /** IID_INetworkListManager */
    private static final Guid.GUID IID_NETWORK_LIST_MANAGER =
            new Guid.GUID("DCB00000-570F-4A9B-8D69-199FDBA5723B");

    /**
     * INetworkListManager 的 vtable 下标。
     *
     * <p><b>这份表是从系统里读出来的，不是照着印象数的</b>（踩过坑）：
     * 这个接口继承 IDispatch，IDispatch 自己占 7 个槽，方法从 7 开始；
     * 而且 {@code GetNetwork} 很容易被漏掉，漏一个下标就全错位 ——
     * 错位之后不一定会崩，只是悄悄按错误的类型写调用方的内存。
     *
     * <p>复核办法：{@code netprofm.dll} 里带着类型库（{@code netlistmgr.dll} 里没有），
     * 用 {@code LoadTypeLib} + {@code ITypeInfo::GetFuncDesc} 读每个方法的 {@code oVft}
     * 就能拿到真实下标（题外话：同一个类型库还暴露了
     * {@code SetSimulatedProfileInfo}（slot 14），那是给测试用的模拟断网入口）。
     */
    private static final int VT_IS_CONNECTED_TO_INTERNET = 11;
    private static final int VT_IS_CONNECTED = 12;
    private static final int VT_GET_CONNECTIVITY = 13;

    /** NLM_CONNECTIVITY 里表示「真的连上了互联网」的那两位。 */
    private static final int CONNECTIVITY_IPV4_INTERNET = 0x40;
    private static final int CONNECTIVITY_IPV6_INTERNET = 0x400;

    /** VARIANT_TRUE / VARIANT_FALSE。 */
    private static final short VARIANT_TRUE = -1;

    /** CLSCTX_INPROC_SERVER|INPROC_HANDLER|LOCAL_SERVER|REMOTE_SERVER —— 让系统自己挑一个能用的。 */
    private static final int CLSCTX_ALL = 23;

    private static final ThreadLocal<Pointer> MANAGER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> COM_UNAVAILABLE = ThreadLocal.withInitial(() -> false);
    /** 「两个来源不一致」的警告只吵一次，别每 5 秒刷屏。 */
    private static final java.util.concurrent.atomic.AtomicBoolean WARNED_MISMATCH =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    static {
        // 结构体要先落到 native 内存里才传得过去（GUID(String) 一般会写，但显式写一次更稳）
        CLSID_NETWORK_LIST_MANAGER.write();
        IID_NETWORK_LIST_MANAGER.write();
    }

    private WindowsInternetState() {
    }

    /** 能不能用 NLM（Windows + COM 对象创建成功）。 */
    public static boolean isAvailable() {
        if (!SystemDetector.isWindows()) {
            return false;
        }
        return manager() != null;
    }

    /**
     * Windows 认为现在能不能上互联网。
     *
     * @return {@code true}/{@code false}；NLM 不可用时返回 {@code null}
     *         （调用方应退回自己的探测，见 {@link NetworkStatus}）
     */
    public static Boolean isConnectedToInternet() {
        Pointer mgr = manager();
        if (mgr == null) {
            return null;
        }
        ShortByReference out = new ShortByReference();
        int hr = invoke(mgr, VT_IS_CONNECTED_TO_INTERNET, out);
        if (hr != 0) {
            Logger("DEBUG", "IsConnectedToInternet 调用失败: 0x" + Integer.toHexString(hr));
            return null;
        }
        boolean result = out.getValue() == VARIANT_TRUE;
        Boolean byFlags = hasInternetFlag();
        if (byFlags != null && byFlags != result && WARNED_MISMATCH.compareAndSet(false, true)) {
            // 两个来源不一致：多半是下标错了（vtable 调用出错不会崩，只会安静地给出错误答案），
            // 所以值得在日志里吵一次
            Logger("WARNING", "系统联网判定不一致: IsConnectedToInternet=" + result
                    + "，连通性标志却是 " + byFlags + " —— 若两者长期相反，请核对 vtable 下标");
        }
        return result;
    }

    /** 有没有任何一个网络连接（不要求能上互联网）。 */
    public static Boolean isConnected() {
        Pointer mgr = manager();
        if (mgr == null) {
            return null;
        }
        ShortByReference out = new ShortByReference();
        int hr = invoke(mgr, VT_IS_CONNECTED, out);
        return hr == 0 ? out.getValue() == VARIANT_TRUE : null;
    }

    /**
     * NLM 的连通性标志位（{@code NLM_CONNECTIVITY}）。
     * 对游戏有用的判断是 {@code (flags & (IPV4_INTERNET | IPV6_INTERNET)) != 0}。
     */
    public static Integer connectivityFlags() {
        Pointer mgr = manager();
        if (mgr == null) {
            return null;
        }
        IntByReference out = new IntByReference();
        int hr = invoke(mgr, VT_GET_CONNECTIVITY, out);
        return hr == 0 ? out.getValue() : null;
    }

    /** 直接看标志位里有没有「互联网」这两位。 */
    public static Boolean hasInternetFlag() {
        Integer flags = connectivityFlags();
        if (flags == null) {
            return null;
        }
        return (flags & (CONNECTIVITY_IPV4_INTERNET | CONNECTIVITY_IPV6_INTERNET)) != 0;
    }

    /** 释放（进程退出前调；不调系统也会清理）。 */
    public static void release() {
        Pointer mgr = MANAGER.get();
        if (mgr != null) {
            release(mgr);
            MANAGER.remove();
        }
    }

    // ==================== 内部 ====================

    /** 取（并按需创建）当前线程上的 INetworkListManager。 */
    private static Pointer manager() {
        if (Boolean.TRUE.equals(COM_UNAVAILABLE.get())) {
            return null;
        }
        Pointer cached = MANAGER.get();
        if (cached != null) {
            return cached;
        }
        try {
            // COM 是按线程初始化的；返回 S_FALSE 表示这个线程已经初始化过，都不算错
            WinNT.HRESULT init = Ole32.INSTANCE.CoInitializeEx(
                    Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED);
            if (init != null) {
                int hr = init.intValue();
                if (hr != 0 && hr != 1 /* S_FALSE：本线程早就初始化过了 */ && hr != WinError.RPC_E_CHANGED_MODE) {
                    Logger("DEBUG", "CoInitializeEx 失败: 0x" + Integer.toHexString(hr));
                    COM_UNAVAILABLE.set(true);
                    return null;
                }
            }
            PointerByReference ppv = new PointerByReference();
            WinNT.HRESULT create = Ole32.INSTANCE.CoCreateInstance(
                    CLSID_NETWORK_LIST_MANAGER,
                    null,
                    CLSCTX_ALL,
                    IID_NETWORK_LIST_MANAGER,
                    ppv);
            if (create == null || create.intValue() != 0 || ppv.getValue() == null) {
                Logger("DEBUG", "创建 NetworkListManager 失败: "
                        + (create == null ? "null" : "0x" + Integer.toHexString(create.intValue())));
                COM_UNAVAILABLE.set(true);
                return null;
            }
            MANAGER.set(ppv.getValue());
            Logger("DEBUG", "已接入 Windows 网络列表管理器（NLM）：联网判定以系统结论为准");
            return ppv.getValue();
        } catch (Throwable t) {
            Logger("DEBUG", "NLM 不可用: " + t);
            COM_UNAVAILABLE.set(true);
            return null;
        }
    }

    /** 按 vtable 下标调一个方法：第 0 个参数是接口指针自己。 */
    private static int invoke(Pointer iface, int index, Object outArg) {
        Pointer vtable = iface.getPointer(0);
        Pointer fnPtr = vtable.getPointer((long) index * Native.POINTER_SIZE);
        Function fn = Function.getFunction(fnPtr, Function.ALT_CONVENTION);
        return fn.invokeInt(new Object[]{iface, outArg});
    }

    private static void release(Pointer iface) {
        try {
            Pointer vtable = iface.getPointer(0);
            Pointer fnPtr = vtable.getPointer(2L * Native.POINTER_SIZE);   // IUnknown::Release
            Function.getFunction(fnPtr, Function.ALT_CONVENTION).invokeInt(new Object[]{iface});
        } catch (Throwable ignored) {
            // 释放失败无所谓
        }
    }
}
