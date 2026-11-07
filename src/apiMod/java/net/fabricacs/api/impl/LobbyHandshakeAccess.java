/* LobbyHandshakeAccess.java — Mixin 向内部桥提供大厅实例状态，不属于 MOD API。 */
package net.fabricacs.api.impl;

public interface LobbyHandshakeAccess {
    LobbyHandshakeBridge acbric$lobbyHandshake();
    /**
     * 重新发出原生准备消息。原生重同步（ResumeScreen 重建大厅）会把所有玩家的 ready 位清零且
     * 不拷贝 readySent，原生也不会自动重发；适配器在玩家此前已经点过准备时用这里恢复意愿。
     */
    void acbric$resendReady();
}
