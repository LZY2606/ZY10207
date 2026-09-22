# 层压证明器（Lamination Prover）

高速电路叠层阻抗审查工具：在选定的**材料版本**下审查某条走线几何的单端/差分阻抗范围，
支持对称叠层编辑、参考平面与走线类型切换、最坏角落与固定种子抽样两类容差分析。
每个失败样本都能回查到**材料批次 / 工艺批**与具体的几何容差取值。

技术栈：Java 17 + Spring Boot 3.3 + SQLite（`sqlite-jdbc`，文件库 `data/lamiprover.db`）+ 原生 SVG 横截面，无前端构建步骤。

## 安装与演示

```bash
# 安装/构建（跳过测试）
mvn -q -DskipTests package

# 演示：先跑全部自动化测试，再以 5547 端口启动本地服务
mvn -q test && mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5547
```

浏览器打开 <http://127.0.0.1:5547>，页面标题为 **“层压证明器”**。

也可以直接运行打包结果：`java -jar target/lamination-prover-1.0.0.jar --server.port=5547`。
数据库路径可用环境变量覆盖：`LAMIPROVER_DB=/path/to.db`。

## 页面能做什么

- 左侧：叠层/材料版本树；当前版本的**关联容差组**与材料批次清单。
- 中间：按真实单位比例绘制的横截面 SVG（标注 w/t/h/s、阻焊层、上下参考平面）；
  公式与**适用范围**；“叠层编辑”保存后产生**新版本**，旧版本上的历史结论不会自动重算。
- 右侧：容差分析（最坏角落 / 固定种子抽样）、汇总、逐点结果（展开可看批次回查）、
  历史运行，以及顶部的**导出 / 导入复核 / 清空数据库**。

## 数据口径（重要）

### 1. 工程单位在进入核心计算前统一为 SI

- 所有长度都带**显式单位**（`m` / `mm` / `um` / `mil`，`1 mil = 25.4 µm`），
  不允许只给一个裸数字。fixture 故意混用三种单位：线宽用 `mil`、铜厚用 `um`、介质高度用 `mm`。
- 阻抗计算（`com.lamiprover.impedance`）只接收 SI（米），由服务层从 `Length.metres()` 归一化后传入；
  页面标称结果同时回显 SI 值（如 `w=1.778e-4 m`），便于核对。
- 绝不凭数值大小猜测单位：`100 um ≠ 100 mil`。绝对容差的单位必须与标称量单位一致，否则拒绝。

### 2. 关联容差组（最坏角落）

- 每个容差都必须显式归属一个 `group`，并记录可回查的 `sourceLot`（材料/工艺批次）。
- **同一压合过程**的介质高度 `h` 与铜厚 `t` 在 fixture 中同属组 `PRESS-LAM-01`
  （批次 `LOT-PRESS-2026-09-A`）。最坏角落中同组因子共享一个 ±1 方向：
  枚举的是 2^G（G=组数）个角落，而不是 2^F（F=因子数）。
- 页面提供“独立取极值（反例模式）”开关：当叠层存在关联组时，服务端**直接拒绝**
  并返回中文原因，不产生任何结论。验收脚本与自动化测试均校验该拒绝行为。
- 抽样模式下每个关联组拥有一条由种子派生的确定性随机流，同组因子共享同一个 `r`。

### 3. 容差分布

- `UNIFORM`：均匀 U[−δ,+δ]；`NORMAL`：正态、σ=δ/3、±3σ 截断；`EXTREME`：只取极值。
- 相对容差按标称百分比；蛇形边缘扇贝（meander/scallop）使用**绝对量、单侧**容差：
  它只减小有效线宽（`weff = w − meander`），建模为非负减宽量，取 0…δ。

### 4. 公式与适用域（越界不报伪精度）

公式为 IPC-2141 族工程闭合式（`Formulas`），页面“公式与适用域”同步展示：

| 类型 | 公式 | 关键适用域 |
| --- | --- | --- |
| 表层微带·单端 | `Z0 = 87/sqrt(εr+1.41)·ln[5.98h/(0.8weff+t)]` | 0.05≤w/h≤4，0<t/h≤0.5，t<h，1≤Dk≤10 |
| 表层微带·差分 | `Zdiff = 2·Z0·(1−0.48·e^(−0.96 s/h))` | 另需 0.05≤s/h≤4 |
| 带状线·单端（对称居中） | `Z0 = 60/sqrt(εr)·ln[1.9h/(0.8w+t)]` | 0.05≤w/h≤6，0<t/h≤0.35，t<h，**不得配阻焊** |
| 带状线·差分 | `Zdiff = 2·Z0·(1−0.347·e^(−2.9 s/h))` | 另需 0.05≤s/h≤4 |

- 微带阻焊覆盖采用填充因子工程加权 `εr* = εr + 0.5·q·(maskDk−1)`，
  `q = maskT/(h+maskT)`，属 IPC-2141 工程近似，页面明确标注。
- **离开适用域时**：结果不含阻抗字段（`impedanceOhm` 为空），只保留输入比值与越界指标
  （实际值 vs 允许范围）。fixture 基础样例在域内；用页面编辑器把 h 改成 20 µm、
  w 改成 0.5 mm 保存为新版本即可看到“只诊断、不报数”的效果（自动化测试 `FormulaDomainTest` 覆盖）。

### 5. 版本化：旧结论不自动重算

- 叠层按“名称 + 版本号”形成**只追加**的版本链，id 形如 `se50-<hash>-v2`，
  `parentVersionId` 指向上一版本；材料版本（如 `S1000-2B@10GHz/r3`）是叠层快照的一部分。
- 分析运行记录（请求参数 + 完整结论 JSON）绑定到执行时的版本 id。
  创建新版本不会触碰旧版本的任何运行记录。
- 运行 id 由“叠层版本 + 分析参数”哈希得到：同版本同参数重放返回同一条历史记录，结论可复现。

## 固定 fixture（空库自动导入）

首次启动且数据库为空时，导入 3 个样例，每个样例自带 2 条运行（32 个关联最坏角落 + 200 固定种子抽样）：

1. `外层单端-SE50`：w=7 **mil**，t=35 **µm**（1 oz），h=0.12 **mm**，Dk 4.2，15 µm 阻焊；
   h 与 t 同属压合组 `PRESS-LAM-01`；蛇形容差 8 µm 单侧；目标 50 Ω ±10%。
2. `外层差分-DI100`：w=0.12 mm、t=1.2 **mil**、h=100 **µm**、s=0.20 mm；目标 100 Ω ±10%。
3. `内层带状线-DI100`：w=6 mil、t=17.5 µm（半盎司）、h=0.42 mm（走线居中、上下平面等距）、
   s=0.25 mm、无阻焊；目标 100 Ω ±10%。

## 运行记录导出 / 清空 / 重导复核

- 页面右上角“导出运行记录”或 `GET /api/export` 下载整库 JSON
  （叠层版本 spec + 每条 run 的 request/report 原样 JSON）。
- `POST /api/reset` 清空数据库；`POST /api/import` 导入导出包（按原 id 幂等 upsert）。
- 复核口径：导入后再次导出，两份包中每条 run 的 `reportJson` 逐字节一致；
  若 run 引用了缺失的叠层版本、或导出包版本不匹配，导入会被拒绝。
  自动化测试 `FullFlowIntegrationTest.exportResetAndReimportReproducesEverything` 完整覆盖该流程。

## 重放方式（确定性）

- 最坏角落：关联组按组名排序，第 i 组方向由枚举序号的第 i 个二进制位决定，顺序固定。
- 抽样：LCG（Numerical Recipes 常数）+ Box–Muller，每组随机流种子为
  `mix(seed, group.hashCode())`；同一 `(seed, N, 叠层定义)` 在任何机器上产生同一组样本，
  不依赖 `java.util.Random` 的 JDK 实现。测试固定种子 `20260922`、N=200。

## 自动化测试

```bash
mvn -q test
```

共 22 个用例（`src/test/java/com/lamiprover`）：

- `LengthUnitTest`：mil/µm/mm/m → SI 换算与单位歧义拒绝。
- `FormulaDomainTest`：标称阻抗量级、越界只返回诊断、带状线拒绝阻焊配置、阻焊降低阻抗。
- `ToleranceEngineTest`：关联组同号、独立极值必然出现 h/t 反号（反例基线）、抽样确定性与组锁定。
- `AnalysisServiceTest`：独立极值被拒、32 个关联角落、失败样本批次回查、越界点无数值、蛇形只减宽。
- `FullFlowIntegrationTest`：首页标题、fixture 装载、HTTP 拒绝独立极值、新版本不重算旧结论、
  导出→清空→重导逐点一致、SVG 横截面可渲染。

## 主要 API

| 方法与路径 | 说明 |
| --- | --- |
| `GET /api/stackups` · `GET /api/stackups/{id}` | 版本列表 / 版本完整定义 |
| `POST /api/stackups` · `POST /api/stackups/{id}/revision` | 新建首版 / 基于旧版创建新版本 |
| `POST /api/stackups/{id}/analyze` | 运行分析（body：mode/targetOhm/tolerancePercent/seed/sampleCount/independentExtremes） |
| `GET /api/stackups/{id}/runs` | 该版本历史运行 |
| `GET /api/stackups/{id}/section.svg` · `/nominal` | 横截面 SVG / 标称阻抗与适用域诊断 |
| `GET /api/formulas/{TRACE_TYPE}` | 公式文本与适用范围 |
| `GET /api/export` · `POST /api/import` · `POST /api/reset` | 运行记录导出 / 重导 / 清空 |

## 目录

```
src/main/java/com/lamiprover/
  unit/        显式单位 Length/LengthUnit（SI 归一化）
  model/       Stackup、TraceType、AnalysisMode
  tol/         容差规格/分布、关联组角落枚举、固定种子抽样
  impedance/   IPC-2141 公式族、适用域检查与越界指标
  db/          SQLite schema、仓储、导出/导入/清空
  service/     分析编排、版本化、fixture、SVG 横截面
  web/         REST API 与叠层草稿 DTO
src/main/resources/static/  操作页面 index.html + app.js
src/test/java/com/lamiprover/  22 个自动化测试
```
