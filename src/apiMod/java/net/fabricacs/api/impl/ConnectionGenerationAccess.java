/* ConnectionGenerationAccess.java — 原生连接代次和握手可发送状态的内部 Mixin 桥。 */
package net.fabricacs.api.impl;

public interface ConnectionGenerationAccess {
    long acbric$connectionGeneration();
    boolean acbric$transportReady();
}
