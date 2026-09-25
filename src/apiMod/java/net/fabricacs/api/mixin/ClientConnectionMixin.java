/* ClientConnectionMixin.java — 观察原生 socket 更换和重连标志，防止准备凭据跨连接复用。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.Client;
import net.fabricacs.api.impl.ConnectionGenerationAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.channels.AsynchronousSocketChannel;

@Mixin(value = Client.class, remap = false)
public abstract class ClientConnectionMixin implements ConnectionGenerationAccess {
    @Shadow private AsynchronousSocketChannel socketChannel;
    @Shadow private boolean attemptFullReconnect;
    @Shadow public abstract boolean isConnecting();
    @Shadow public abstract boolean isDisconnected();
    @Unique private AsynchronousSocketChannel acbric$socket;
    @Unique private boolean acbric$reconnecting;
    @Unique private volatile long acbric$generation;

    @Inject(method = "tick()V", at = {@At("HEAD"), @At("RETURN")}, remap = false)
    private void acbric$observeConnection(CallbackInfo ci) {
        if (socketChannel != acbric$socket || attemptFullReconnect != acbric$reconnecting) {
            acbric$socket = socketChannel; acbric$reconnecting = attemptFullReconnect; acbric$generation++;
        }
    }
    @Override public long acbric$connectionGeneration() { return acbric$generation; }
    @Override public boolean acbric$transportReady() {
        return socketChannel != null && socketChannel.isOpen() && !attemptFullReconnect && !isConnecting() && !isDisconnected();
    }
}
