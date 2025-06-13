package org.zk.cpca;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zk.cpca.core.AddressParser;
import org.zk.cpca.core.FilterCondition;
import org.zk.cpca.model.ParseResult;

import java.util.List;

/**
 * 测试用例
 *
 * @author zk
 */
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
        List<ParseResult> results = parser.parse("湖北省黄石市下陆区团城山");
        Assertions.assertEquals(1, results.size());

        ParseResult result = results.get(0);
        Assertions.assertEquals(result.getProvince(), "湖北省");
        Assertions.assertEquals(result.getCity(), "黄石市");
        Assertions.assertEquals(result.getArea(), "下陆区");
        Assertions.assertNull(result.getTown());
    }

    /**
     * 部分地址测试，由于全国有6个‘保安镇’，因此会解析出6个省市区镇出来
     */
    @Test
    void testPartialAddress() {
        List<ParseResult> results = parser.parse("保安镇大王村");
        Assertions.assertEquals(6, results.size());
    }

    /**
     * 指定限定区划测试
     * 在一个地址可能会解析出多个区划的时候，可以限定一个范围来缩小解析结果数
     */
    @Test
    void testWithFilter() {
        FilterCondition filter = new FilterCondition();
        filter.setCity("平顶山市");

        List<ParseResult> results = parser.parse("保安镇大王村", filter);
        Assertions.assertEquals(1, results.size());

        ParseResult result = results.get(0);
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
        List<ParseResult> results = parser.parse("新疆伊犁霍尔果斯市");
        Assertions.assertEquals(1, results.size());

        ParseResult result = results.get(0);
        Assertions.assertEquals(result.getProvince(), "新疆维吾尔自治区");
        Assertions.assertEquals(result.getCity(), "伊犁哈萨克自治州");
        Assertions.assertEquals(result.getArea(), "霍尔果斯市");
        Assertions.assertNull(result.getTown());
    }

    /**
     * 测试所有
     */
    @Test
    void testAll() {
        testCompleteAddress();
        testPartialAddress();
        testWithFilter();
        testShortAddress();
    }
}