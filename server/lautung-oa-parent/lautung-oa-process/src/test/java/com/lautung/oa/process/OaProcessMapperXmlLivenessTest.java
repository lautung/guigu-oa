package com.lautung.oa.process;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手写 XML 不吃实体的 {@code @TableLogic}——它只进 MyBatis-Plus 生成的 SQL（ADR 0050，
 * 缺口 #198），所以本文件的每条读都得自己 include 一次 {@code approvalRowLiveness}。
 *
 * <p>护栏刻意只覆盖 {@code OaProcessMapper.xml}：全仓规则会误伤
 * {@code oa_process_template_publication}——那张证据表刻意没有 {@code is_deleted}（ADR 0031）。
 *
 * <p>断言形状是「include 存活片段，或 SQL 里出现 {@code a.is_deleted}」，不是「出现
 * {@code is_deleted}」：{@code countProcessedRows}/{@code countCarbonCopiedRows} 有
 * {@code r.}/{@code g.} 自己的 is_deleted 过滤，只断同一个词会让审批行本体漏过滤的语句照样绿。
 */
public class OaProcessMapperXmlLivenessTest {

    private static final String MAPPER_RESOURCE =
            "/com/lautung/oa/process/mapper/xml/OaProcessMapper.xml";
    private static final String LIVENESS_FRAGMENT = "approvalRowLiveness";

    @Test
    public void livenessFragmentIsTheApprovalRowPredicate() throws Exception {
        NodeList fragments = document().getElementsByTagName("sql");
        Element liveness = null;
        for (int i = 0; i < fragments.getLength(); i++) {
            Element fragment = (Element) fragments.item(i);
            if (LIVENESS_FRAGMENT.equals(fragment.getAttribute("id"))) {
                liveness = fragment;
                break;
            }
        }
        assertNotNull(liveness, "存活片段 " + LIVENESS_FRAGMENT + " 缺失");
        assertEquals("a.is_deleted = 0", normalize(sqlText(liveness)),
                "存活片段被改动：审批行本体的谓词必须就是 a.is_deleted = 0");
    }

    @Test
    public void everyReadFiltersTheApprovalRowItself() throws Exception {
        List<String> offenders = new ArrayList<>();
        NodeList selects = document().getElementsByTagName("select");
        // 防呆：解析不到语句时下面的循环会空转成绿。
        assertTrue(selects.getLength() > 0, "mapper XML 里没解析到任何 <select>");
        for (int i = 0; i < selects.getLength(); i++) {
            Element select = (Element) selects.item(i);
            if (!includesLiveness(select) && !sqlText(select).contains("a.is_deleted")) {
                offenders.add(select.getAttribute("id"));
            }
        }
        assertTrue(offenders.isEmpty(),
                "这些读没有过滤审批行本体存活，补 <include refid=\"" + LIVENESS_FRAGMENT + "\"/>: "
                        + offenders);
    }

    /**
     * 已处理谓词是动作类型白名单（#287，ADR 0049 修订）：count 与行读两处都必须按
     * {@code action_type IN (APPROVE, REJECT)} 过滤，且不得回到 description 负匹配——
     * 新动作（转办/催办/加签发起/撤回）一旦被负匹配全量误收，白名单合同就失效了。
     */
    @Test
    public void processedPredicatesAreTheActionTypeWhitelist() throws Exception {
        for (String id : List.of("countProcessedRows", "selectProcessedRows")) {
            Element select = selectById(id);
            assertNotNull(select, "已处理读 " + id + " 缺失");
            String sql = normalize(sqlText(select)).toLowerCase();
            assertTrue(sql.contains("r.action_type in ('approve', 'reject')"),
                    "已处理读 " + id + " 必须用动作类型白名单 action_type IN (APPROVE, REJECT)");
            assertFalse(sql.contains("description !="),
                    "已处理读 " + id + " 不得回到 description 负匹配（ADR 0049 修订）");
        }
    }

    private Element selectById(String id) throws Exception {
        NodeList selects = document().getElementsByTagName("select");
        for (int i = 0; i < selects.getLength(); i++) {
            Element select = (Element) selects.item(i);
            if (id.equals(select.getAttribute("id"))) {
                return select;
            }
        }
        return null;
    }

    private static boolean includesLiveness(Element statement) {
        NodeList includes = statement.getElementsByTagName("include");
        for (int i = 0; i < includes.getLength(); i++) {
            if (LIVENESS_FRAGMENT.equals(((Element) includes.item(i)).getAttribute("refid"))) {
                return true;
            }
        }
        return false;
    }

    /** 只收文本/CDATA：注释里写到的 is_deleted 不算数。 */
    private static String sqlText(Node node) {
        StringBuilder text = new StringBuilder();
        collectText(node, text);
        return text.toString();
    }

    private static void collectText(Node node, StringBuilder text) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE
                    || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectText(child, text);
            }
        }
    }

    private static String normalize(String sql) {
        return sql.trim().replaceAll("\\s+", " ");
    }

    private static Document document() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // mapper 的 DOCTYPE 指向 mybatis.org 的 DTD：这条断言不该依赖网络可达。
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        try (InputStream mapper =
                     OaProcessMapperXmlLivenessTest.class.getResourceAsStream(MAPPER_RESOURCE)) {
            assertNotNull(mapper, "mapper XML 不在测试 classpath 上: " + MAPPER_RESOURCE);
            return factory.newDocumentBuilder().parse(mapper);
        }
    }
}
