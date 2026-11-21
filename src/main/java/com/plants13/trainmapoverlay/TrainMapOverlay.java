// SPDX-License-Identifier: MIT

package com.plants13.trainmapoverlay;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.common.Mod;

/**
 * Create: Train Map Overlay 的入口。
 *
 * <p>本类只负责挂载；服务端采集、网络同步、客户端地图接入分别在各自的包中。
 */
@Mod(TrainMapOverlay.MOD_ID)
public final class TrainMapOverlay {

    public static final String MOD_ID = "trainmapoverlay";
    public static final String LOG_PREFIX = "[TMO]";

    public static final Logger LOGGER = LogUtils.getLogger();

    public TrainMapOverlay() {
        LOGGER.info("{} loaded", LOG_PREFIX);
    }
}
