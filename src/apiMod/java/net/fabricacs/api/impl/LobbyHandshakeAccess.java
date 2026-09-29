/* LobbyHandshakeAccess.java — Mixin 向内部桥提供大厅实例状态，不属于 MOD API。 */
package net.fabricacs.api.impl;

public interface LobbyHandshakeAccess {
    LobbyHandshakeBridge acbric$lobbyHandshake();
}
