# 素材来源与授权

| 素材 | 来源 | 授权 | 用在哪里 |
|---|---|---|---|
| 木纹贴图（花梨 rosewood_veneer1、沙比利 sapele_veneer、胡桃 walnut_veneer、深色木 wood_table_001 / dark_wood、柚木 teak_veneer） | Poly Haven（polyhaven.com） | CC0 | 所有木构件；缩到 1024×1024 后嵌进模型 |
| 木头撞击音效（impactWood、impactPlank、impactBell） | Kenney「Impact Sounds」（kenney.nl） | CC0 | 拖动时的咔哒声、推不动的闷响、拼到位、木条拆出、打开器物；用 ffmpeg 剪短 |
| 背景音乐《Asianoriental2》 | OpenGameArt（opengameart.org/content/asianoriental2） | CC0 | 循环背景音乐，响度统一到 -20 LUFS；另外 3 首 CC0 备选的试听版在 `docs/音乐候选/` |
| 思源宋体 Noto Serif SC Bold | Google / Adobe（github.com/notofonts/noto-cjk） | SIL Open Font License 1.1 | 华容道木块上的名字、鲁班盒盖和鲁班尺上的字（转成模型，不随应用分发字体文件） |

青铜、鎏金、黄铜贴图由 `tools/make_metal_tex.py` 用程序噪声生成；木作工坊全景图由 `tools/make_workshop.py` 在 Blender 里搭场景渲染，贴在 `tools/make_sky.py` 生成的内翻球上；营造图谱的线描图由 `tools/make_atlas.py` 用 Blender 描线渲染。这些都不涉及外部素材。

所有器物模型都由 `tools/` 里的 Blender 脚本程序化生成，几何数据直接来自游戏规则代码（鲁班锁的榫口、鲁班盒的木板尺寸、斗拱的构件位置），没有使用 AI 生成的模型。
