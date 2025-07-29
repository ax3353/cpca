# CPCA 地址解析器

一个纯 Java 实现的中国行政区划地址解析库，支持省市区街道的智能拆解，支持别名匹配、简称还原、限定范围筛选等高级功能。

---

## 🧩 项目特性

- 📍 支持解析完整地址，如 `湖北省黄石市下陆区团城山`
- 🔍 支持模糊解析（部分地址），如 `保安镇大王村` → 多个候选结果
- ⚙️ 支持按城市、省份等维度筛选解析结果（`FilterCondition`）
- 🧠 支持“简称模式”：如 `新疆伊犁` 还原为 `新疆维吾尔自治区伊犁哈萨克自治州`
- 🗂️ 支持外部行政区划数据源 json 文件加载

---

## 📦 快速开始

### 1. 引入依赖

> 暂未发布至 Maven 中央仓库，请手动克隆或本地依赖：

```bash
git clone https://github.com/ax3353/cpca.git
```

### 2. 用法示例
```java
public class AddressParserTest {
    private AddressParser parser;

    @BeforeEach
    void setUp() {
        // 1. 使用默认文件（resources/cpca_2025.json）
        parser = new AddressParser();
    }

    /**
     * 全称测试
     */
    @Test
    void testCompleteAddress() {
        ParseResult result = parser.parse("湖北省黄石市下陆区团城山");
        Assertions.assertEquals(result.getProvince(), "湖北省");
        Assertions.assertEquals(result.getCity(), "黄石市");
        Assertions.assertEquals(result.getArea(), "下陆区");
        Assertions.assertNull(result.getTown());
    }

    /**
     * 指定限定区划测试
     * 在一个地址可能会解析出多个区划的时候，可以限定一个范围来缩小解析结果数
     */
    @Test
    void testWithFilter() {
        FilterCondition filter = new FilterCondition();
        filter.setCity("平顶山市");

        ParseResult result = parser.parse("保安镇大王村", filter);
        Assertions.assertEquals(result.getProvince(), "河南省");
        Assertions.assertEquals(result.getCity(), "平顶山市");
        Assertions.assertEquals(result.getArea(), "叶县");
        Assertions.assertEquals(result.getTown(), "保安镇");
    }

    /**
     * 简称测试
     * 支持简称的地址，解析出完整的区划
     */
    @Test
    void testShortAddress() {
        ParseResult result = parser.parse("新疆伊犁霍尔果斯市");
        Assertions.assertEquals(result.getProvince(), "新疆维吾尔自治区");
        Assertions.assertEquals(result.getCity(), "伊犁哈萨克自治州");
        Assertions.assertEquals(result.getArea(), "霍尔果斯市");
        Assertions.assertNull(result.getTown());
    }

    /**
     * 简单测试
     */
    @Test
    void testAddress() {
        ParseResult result = parser.parse("长泰县兴泰开发区");
        Assertions.assertEquals(result.getProvince(), "福建省");
        Assertions.assertEquals(result.getCity(), "漳州市");
        Assertions.assertEquals(result.getArea(), "长泰县");
        Assertions.assertNull(result.getTown());
    }

    /**
     * 测试所有
     */
    @Test
    void testAll() {
        testCompleteAddress();
        testWithFilter();
        testShortAddress();
        testAddress();

        System.out.println(parser.parse("吉林省洮南市经济技术开发区经开大厦408室"));
        System.out.println(parser.parse("前郭县巴特尔社区文贸委房屋开发93-1号楼5单元102室"));
        System.out.println(parser.parse("保安镇大王村"));
        System.out.println(parser.parse("合肥市包河经济开发区乌鲁木齐路389号4栋101室"));
        System.out.println(parser.parse("威海市重庆街112号"));
        System.out.println(parser.parse("日照市经济开发区上海路556号3楼303室"));
        System.out.println(parser.parse("内蒙古鄂托克前旗上海庙镇特布德嘎查1社"));
        System.out.println(parser.parse("长沙市雨花区韶山南路633号上海城19栋2612"));
        System.out.println(parser.parse("龙潭区靠山街"));
        System.out.println(parser.parse("敦化市胜利街城西工业园区"));
        System.out.println(parser.parse("吉林省吉林开发区"));
        System.out.println(parser.parse("山东省日照市经济开发区北京路街道银川路与天津路交界处南100米"));
    }
}
```
