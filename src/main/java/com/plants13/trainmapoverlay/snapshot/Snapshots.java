// SPDX-License-Identifier: MIT

package com.plants13.trainmapoverlay.snapshot;

import java.util.List;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 服务端快照的内部数据结构。与 API 包的记录分开，避免内部字段泄露到外部接口。
 */
public final class Snapshots {

    private Snapshots() {}

    /** 一节车厢的首尾点（世界坐标，按 TravellingPoint 计算，不依赖实体加载）。 */
    public record Carriage(Vec3 leading, Vec3 trailing, ResourceKey<Level> dimension) {}

    /** 一列车的快照。carriages 与 Train.carriages 下标一一对应。 */
    public record Train(UUID id, ResourceKey<Level> dimension, List<Carriage> carriages) {}

    /** 一个车站的快照。 */
    public record Station(UUID id, String name, ResourceKey<Level> dimension, Vec3 position) {}
}
