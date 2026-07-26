# Monaco Testkit 设计文档

> 状态：Proposed
>
> 更新日期：2026-07-25

本目录定义 Monaco 新架构的测试基础设施和可执行测试清单：

- [Testkit 实现方案](testkit-implementation.md)：模块边界、公共 API、包结构、依赖方式、确定性时间、故障注入及分阶段交付。
- [测试 Case](test-cases.md)：按协议、Core、Store、Transport、Broker、Plugin 和 Cluster 分类的 Given/When/Then 用例及追踪矩阵。
- [testkit 实现任务](../modules/testkit.md)：基础 testkit 的 TK-P0 至 TK-P4 可执行任务、前置依赖和验收标准。
- [cluster-testkit 实现任务](../modules/cluster-testkit.md)：独立集群测试模块的 C1 至 C6 契约、fixture 和故障任务。

设计依据：

- [MQTT 5.0 架构设计](../mqtt5-architecture.md)
- [MQTT 5.0 技术实现方案](../mqtt5-technical-implementation.md)
- [MQTT 5.0 模块详细设计](../mqtt5-module-design.md)
- [MQTT 插件系统设计](../plugin-system.md)
- [MQTT 5.0 集群架构设计](../mqtt5-cluster-architecture.md)
- [MQTT 5.0 集群设计](../mqtt5-cluster-design.md)
- [MQTT 5.0 集群模块详细设计](../mqtt5-cluster-module-design.md)

本文档中的 P0-P4 对应单机实现阶段，C0-C6 对应最新集群模块实施阶段。早期集群设计中的 R1-R3 在模块详细设计中收敛为 C5。尚未进入 `settings.gradle.kts` 的集群模块及 `cluster-testkit` 只定义未来契约，不进入当前 testkit 的编译依赖。
