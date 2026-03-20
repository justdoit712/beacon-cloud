package com.cz.webmaster.mapper;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class ClientBalanceMapperContractTest {

    @Test
    public void shouldOnlyExposeBalanceTrueSourceOperations() {
        Set<String> actualMethods = new LinkedHashSet<>();
        for (Method method : ClientBalanceMapper.class.getDeclaredMethods()) {
            actualMethods.add(method.getName());
        }

        Set<String> expectedMethods = new LinkedHashSet<>(Arrays.asList(
                "selectByClientId",
                "insertInitialBalance",
                "debitBalanceAtomic",
                "rechargeBalanceAtomic",
                "adjustBalanceAtomic"
        ));

        Assert.assertEquals(expectedMethods, actualMethods);
    }

    @Test
    public void shouldKeepMapperXmlOnAtomicBalanceContractOnly() throws Exception {
        String xml = loadMapperXml();

        Assert.assertTrue(xml.contains("<select id=\"selectByClientId\""));
        Assert.assertTrue(xml.contains("<insert id=\"insertInitialBalance\""));
        Assert.assertTrue(xml.contains("<update id=\"debitBalanceAtomic\""));
        Assert.assertTrue(xml.contains("<update id=\"rechargeBalanceAtomic\""));
        Assert.assertTrue(xml.contains("<update id=\"adjustBalanceAtomic\""));

        Assert.assertFalse(xml.contains("selectByPrimaryKey"));
        Assert.assertFalse(xml.contains("insertSelective"));
        Assert.assertFalse(xml.contains("updateByPrimaryKeySelective"));
        Assert.assertFalse(xml.contains("extend4"));

        Assert.assertTrue(xml.contains("where client_id = #{clientId,jdbcType=BIGINT}"));
        Assert.assertTrue(xml.contains("and is_delete = 0"));
        Assert.assertTrue(xml.contains("balance = balance - #{fee,jdbcType=BIGINT}"));
        Assert.assertTrue(xml.contains("balance = balance + #{amount,jdbcType=BIGINT}"));
        Assert.assertTrue(xml.contains("balance = balance + #{delta,jdbcType=BIGINT}"));
    }

    private String loadMapperXml() throws Exception {
        InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("mapper/ClientBalanceMapper.xml");
        Assert.assertNotNull("mapper xml should exist", inputStream);
        try (InputStream in = inputStream) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }
}
