// SPDX-License-Identifier: MIT

package com.plants13.trainmapoverlay.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 对外 API（版本号见 {@link #API_VERSION}）。
 *
 * <p>仅客户端渲染线程调用。叠加层关闭或未加载时返回空结果，不抛异常。
 * 草案签名来自 docs 交接文件 03；实现前以本文件为准。
 */
public final class TrainOverlayApi {

    public static final int API_VERSION = 1;

    private TrainOverlayApi() {}

    /** 按 UUID 取列车的车厢快照；未知或叠加层关闭时为空。 */
    public static Optional<TrainSnapshot> getTrain(UUID id) {
        return Optional.empty();
    }

    /** 某维度下的车站列表；叠加层关闭时为空集合。 */
    public static Collection<StationSnapshot> getStations(ResourceKey<Level> dimension) {
        return List.of();
    }

    /** 车厢的首尾点与所在维度，position 为插值后的中点。 */
    public record CarriageSnapshot(Vec3 leading, Vec3 trailing, ResourceKey<Level> dimension) {
        public Vec3 position(float partialTicks) {
            return leading.lerp(trailing, 0.5);
        }
    }

    public record TrainSnapshot(UUID id, List<CarriageSnapshot> carriages) {}

    public record StationSnapshot(UUID id, String name, ResourceKey<Level> dimension, Vec3 position) {}
}
