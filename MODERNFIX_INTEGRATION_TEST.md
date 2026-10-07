# ModernFix Dynamic Resources 集成测试

## 已完成的改动

### 1. 代码改动
- **`ModernFixIntegration.java`**：实现 `ModernFixClientIntegration` 接口
  - `onDynamicResourcesStatusChange`：监听动态资源开关状态
  - `onBakedModelLoad`：在动态资源模式下，每个模型加载时包装它
  
- **`ModelWrappingHandler.java`**：改造批量包装逻辑
  - 动态资源**关闭**时：保持原有的批量包装（遍历 `getModels().keySet()`）
  - 动态资源**开启**时：跳过批量包装，由 `ModernFixIntegration` 按需包装

- **`META-INF/services`**：注册 ModernFix 集成点
  - Java SPI 自动发现机制，ModernFix 会在启动时加载我们的集成类

### 2. 配置改动
- **`build.gradle`**：添加 `modCompileOnly 'modernfix:modernfix-forge-5.27.85+mc1.20.1'`
- **`run/config/modernfix-common.toml`**：开启 `mixin.perf.dynamic_resources = true`

## 测试步骤

### 测试 1：动态资源关闭（原有行为）
1. 编辑 `run/config/modernfix-common.toml`，注释掉或删除 `dynamic_resources = true`
2. 启动游戏，观察日志：
   - 应该看到 `[Constancy/ModernFix] ModernFix dynamic_resources: disabled`
   - 资源重载时，`ModelWrappingHandler` 走批量包装路径
3. 加入世界，确认 CTM 纹理正常工作

### 测试 2：动态资源开启（新集成）
1. 编辑 `run/config/modernfix-common.toml`，保持 `dynamic_resources = true`
2. 启动游戏，观察日志：
   - 应该看到 `[Constancy/ModernFix] ModernFix dynamic_resources: enabled`
   - 资源重载时，`ModelWrappingHandler` 跳过批量包装
3. 加入世界，确认：
   - CTM 纹理仍然正常工作（说明按需包装生效）
   - 内存占用应该比测试 1 低（可选，用 F3 看）
   - 资源重载时间应该比测试 1 短（可选，观察进度条）

### 预期日志关键字

**动态资源关闭**：
```
[Constancy/ModernFix] ModernFix dynamic_resources: disabled
```

**动态资源开启**：
```
[Constancy/ModernFix] ModernFix dynamic_resources: enabled
```

## 潜在问题排查

### 问题 1：集成类没被加载
- 检查 `build/resources/main/META-INF/services/` 下是否有正确的 service 文件
- 确认 JAR 里打包了这个 service 文件：`jar -tf build/libs/*.jar | grep services`

### 问题 2：动态资源下 CTM 不生效
- 检查日志里 `onBakedModelLoad` 是否被调用
- 确认 `wrappingHandler` 不是 `null`
- 确认传入的 `location` 能匹配到受影响的方块状态

### 问题 3：编译时找不到 ModernFix API
- 确认 `build.gradle` 里有 `modCompileOnly 'modernfix:...'`
- 确认 `repositories` 里有 `flatDir { dirs 'lib' }`
- 运行 `gradlew --refresh-dependencies compileJava`

## 下一步

如果两个测试都通过，说明集成成功。可以：
1. 测量具体的内存和加载时间差异（用 VisualVM / JProfiler）
2. 继续第三条优化（`CtmQuadTransform` 的细节优化）
3. 打包成 JAR，在生产环境测试
