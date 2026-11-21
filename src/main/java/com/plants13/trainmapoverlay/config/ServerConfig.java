// SPDX-License-Identifier: MIT

package com.plants13.trainmapoverlay.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 服务端配置（R8、R9、R11）。
 *
 * <p>总开关 {@link #enabled} 关闭时，采集、网络发送全部停止。
 */
public final class ServerConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue SYNC_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue MAX_TRAINS_PER_PACKET;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("overlay");
        ENABLED = b
                .comment("Master switch. When false, collection and network sends stop entirely.")
                .define("enabled", true);
        SYNC_INTERVAL_TICKS = b
                .comment("Server ticks between train snapshot collections (R9). Lower = fresher, more bandwidth.")
                .defineInRange("syncIntervalTicks", 10, 1, 200);
        MAX_TRAINS_PER_PACKET = b
                .comment("Upper bound of trains encoded in one packet; larger snapshots are split (R9).")
                .defineInRange("maxTrainsPerPacket", 64, 1, 512);
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {}
}
