# 层压证明器（Laminate Prover）

面向高速电路叠层评审的本地服务：工程师给定走线几何、参考平面与材料版本，
系统用 IPC 工程经验式计算单端/差分阻抗，并在制板厂给出的标称值与容差分布下做
**最坏关联角落**与**固定种子抽样**，给出阻抗范围、失败样本的批次/几何溯源；
离开公式适用域时只保留输入与越界指标，不输出伪精确数字。

技术栈：Java 17（在本机 JDK 24/26 上同样构建运行）、Spring Boot 3.5、SQLite（JDBC）、
服务端生成 SVG、无外部前端依赖的单页操作界面。

## 运行

```bash
# 仅打包
mvn -q -DskipTests package

# 自动化测试 + 启动演示（端口 5547）
mvn -q test && mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5547
```

打开 <http://127.0.0.1:5547>，页面标题为「层压证明器」。

SQLite 文件默认是工作目录下的 `laminate-prover.db`，可用环境变量覆盖：

```bash
LAMPROVER_DB=/path/to/prover.db mvn -q spring-boot:run
```

首次启动若数据库为空，会自动播种固定 fixture（板号 `DEMO-4L-001`，v1）。

## 数据口径（务必先读）

### 单位：显式工程单位，进入核心计算前统一 SI

每个几何/材料量都是 `{value, unit}`。支持：

| unit | 含义 | SI 系数 |
| --- | --- | --- |
| `um` | 微米 | 1e-6 m |
| `mm` | 毫米 | 1e-3 m |
| `mil` | 密耳（千分之一英寸） | 2.54e-5 m |
| `1` | 无量纲（相对介电常数 Dk） | — |
| `ohm` | 欧姆（目标带） | — |

**禁止按数值大小猜测单位**：`10 mil` 与 `10 um` 的 SI 值相差 25.4 倍。
所有容差界、公式比例（w/h、s/h、t/w、hm/h）都在 SI 换算后计算，
因此与用户混用 mil/µm/mm 无关。

### 容差：分布形式与关联批次

每个量的容差 `ToleranceSpec`：

- `lowerPct/upperPct`：百分比界（如 10 表示 ±10%）；也可给同单位的绝对界 `lowerAbs/upperAbs`，
  两者取更宽者。
- `distribution`：
  - `uniform`：均匀分布，样本在 [下界, 上界] 内等概率；
  - `normal`：正态分布，**给出的界按 ±3σ 解释**，样本经逆正态 CDF 映射并截断在界内。
- `lotId`：**关联批次/同一次工艺过程**。同 `lotId` 的多个量不允许独立取极值：
  - 最坏角落：整个批次只占一个方向位（压合偏厚/偏高或偏薄/偏低），
    同批每个变量仍取各自的界；
  - 固定抽样：为整个批次抽一个共享标准化 `u`，同批所有量按各自分布映射，
    因此“介质厚度偏厚”时“同批铜厚”也一定偏厚，符合同一次压合的物理实际。
- `role` / `processHint`：批次内角色与工艺说明，用于失败样本的人类可读溯源。

没有 `lotId` 的量自动成为独立单变量批次，可独立取极值。

> **验收口径**：逐变量独立取极值（例如把同一压合批次的半固化片厚度取上界、
> 铜厚取下界）会被系统拒绝。分析接口传 `policy=independent-extrema` 返回 HTTP 422；
> 页面“尝试独立取极值”按钮会给出冲突批次名与冲突量。
> 固定策略为 `correlated-press-batches`。

蛇形（等长绕线）容差（幅度/节距/长度）只影响长度与相位，**明确不参与阻抗**：
编辑器保留输入，但变量表不含蛇形量。

### 公式与适用域

实现于 `ImpedanceFormulas`，页面「公式与适用域」标签同步展示：

1. **IPC-2141 表面微带单端**：`Z0 = 87/√(er+1.41)·ln(5.98h/(0.8·we))`，
   `we` 用 Hammerstad-Jensen 有限铜厚修正。
2. **IPC 居中带状线单端**：`Z0 = 60/√er·ln(1.9(2b+t)/(0.8w+t))`。
3. **IPC 边缘耦合微带差分**：`Zd = 2·Z0/(1 + 0.48·exp(-0.96·s/h))`。
4. **IPC 边缘耦合带状线差分**：`Zd = 2·Z0/(1 + 0.347·exp(-2.9·s/b))`。
5. **阻焊一阶降额（Bogatin 工程式）**：
   `Zm = Z0/(1 + (Z0/87)·(hm/h)·(erm-1)/er·2.5)`。

适用域（越界即只给诊断）：

- `w/h ∈ [0.05, 8]`、`t/w ≤ 0.5`、`t/h ≤ 0.5`；
- 介质 `Dk ∈ [1.05, 12]`；
- 差分耦合比 `s/h` 或 `s/b ∈ [0.05, 8]`；
- 阻焊 `hm/h ≤ 0.3`、阻焊 `Dk ∈ [2, 6]`；
- 带状线要求走线位于两参考平面之间 ±5% 居中度内（本版本不实现偏移带状线）。

越界点：`inDomain=false`、`zOhm=null`（NaN 不出现在 JSON）、
`violations[]` 给出参数名/规则/实际值/限值。标称、角落、样本同规则。

### 叠层与版本

- 层按**从上到下**存储；`symmetric=true` 时校验层种类/铜角色/材料关于中心镜像
  （厚度不必相同，厚度差异由容差表达）。
- 参考平面 = `COPPER` 且 `copperRole=REFERENCE_PLANE`。
  - 走线单侧有平面 → 微带；外表面方向存在 `SOLDER_MASK` 时计入阻焊。
  - 双侧有平面 → 带状线；`b` 为两平面间介质厚度和，`Dk` 按厚度加权。
  - 两侧都无平面 → 结构诊断，阻抗无定义。
- **保存即新版本**：同一 `boardCode` 再次保存生成 v2、v3……
  旧版本载荷与其运行结论永不改写、不随编辑自动重算。

## 固定 fixture（混合单位 + 同批压合容差）

`DEMO-4L-001`（材料版本 `FR4-7628@revC`），9 层对称结构，单位刻意混用：

- 阻焊 `0.5 mil`（±10%，lot `mask-top`）；
- L1/L4 铜厚 `35 µm`（±10%，分别 lot `press-top` / `press-bottom`）；
- 顶部半固化片 `0.25 mm`（±7% normal，lot `press-top`，与 L1 铜厚同批）；
- 底部半固化片 `250 µm`（±7% normal，lot `press-bottom`）；
- 芯板 `47 mil`（±5% normal，lot `core-A`，与 L2/L3 铜厚同批）；
- Dk 各自材料批次（`dk-pp`、`dk-core`、`dk-mask`，normal ±3%）；
- 外层蚀刻 lot `etch-A` 绑定线宽/线距。

走线：

| 走线 | 类型 | 几何 | 目标带 | 预期 |
| --- | --- | --- | --- | --- |
| `SE-50-L1` | 单端 | w=0.30 mm | 50 Ω ±10% (45–55 Ω) | 标称约 50.5 Ω，关联角落/200 抽样全部通过 |
| `USB-DP-L1` | 差分 | w=0.22 mm, s=0.25 mm | 90–110 Ω | 标称约 96 Ω，全部通过 |
| `WIDE-OOD-L1` | 单端 | w=3 mm（w/h=12） | — | **离开适用域，只给 w/h 越界诊断** |

固定种子 `20260922`、200 样本；LCG 与逆正态 CDF 均为内置确定性实现，跨机器可重放。

## 溯源：失败样本回到批次与几何

每个角落/样本点带：

- `lotDirections`：各关联批次取的是 `+high` 还是 `-low`；
- `lotDraws`：抽样时每批次的共享 `u`；
- `traceability`：每个变量的计算明细（例如
  `lot=press-top u=0.2193 normal z=-0.77 frac=-1.807%` 或
  `lot=etch-A u=0.81 uniform[...]`），可直接回查到材料批次与该几何量的取值。

## 运行记录的导出 / 清空 / 导入复核

- `GET /api/export`：导出 bundle（全部版本载荷 + 运行结果）。
- `DELETE /api/admin/database`：清空叠层版本与运行记录。
- `POST /api/import?replaceAll=true|false`：导入 bundle；
  对每个版本重算载荷 SHA-256、对每条 run 比对 `inputHash`，
  任何哈希不一致都拒绝并列入 `notes`，从而保证“清空后重新导入复核”。

页面「导入 / 导出」标签提供下载与文件上传。

## 主要 HTTP 接口

| 方法/路径 | 说明 |
| --- | --- |
| `GET /api/formulas` | 公式、表达式、适用域与出处 |
| `GET /api/fixture` | 固定 fixture 叠层（未入库的编辑草稿） |
| `POST /api/stackups` | 校验并保存为新版本 |
| `GET /api/stackups/{code}/versions/{v}` | 读取某版本（含 payloadHash） |
| `GET /api/stackups/{code}/versions/{v}/svg?trace=` | SVG 横截面 |
| `POST /api/preview` | 用请求体中的叠层即时计算（不落库） |
| `POST /api/stackups/{code}/versions/{v}/analyze?seed=&samples=&policy=` | 分析并存运行记录 |
| `POST .../independent-extrema-check` | 独立取极值冲突诊断（应 rejected） |
| `GET/DELETE /api/runs[/{id}]` | 运行记录列表/详情/删除 |
| `GET /api/export`、`POST /api/import`、`DELETE /api/admin/database` | 复核闭环 |

## 自动化测试

`mvn -q test` 共 34 个用例，覆盖：

- mil/µm/mm SI 换算不可猜测；
- 同压合批次角落同向、独立极值冲突检测、抽样确定性与批次共享方向；
- 公式数值合理性与各适用域越界诊断（含非居中带状线、过厚铜/阻焊、越界 Dk）；
- fixture 混合单位、关联角落通过、宽线越界只给诊断、蛇形不入阻抗变量；
- Spring Boot：首页标题、422 拒绝独立策略、版本不可变、导出→清空→导入哈希复核。

## 目录

```
src/main/java/com/lamprover/
  domain/     单位、容差、叠层/层/走线等不可变记录
  core/       SI 解析、关联批次机器、公式与适用域、几何解析、分析器
  persistence SQLite 建表与版本/运行记录仓储（JdbcTemplate）
  service/    固定 fixture、分析服务、SVG、导出导入、启动播种
  web/        REST API 与首页
src/main/resources/
  static/     index.html + app.js（无框架单页）
src/test/     JUnit 5 / Spring Boot Test
```
