package com.cz.common.util;

import com.cz.common.model.StandardSubmit;
import org.junit.Assert;
import org.junit.Test;

public class CMPPSubmitRepoMapUtilTest {

    @Test
    public void shouldPutGetAndRemoveSubmitBySequence() {
        int sequence = 123456;
        StandardSubmit submit = new StandardSubmit();
        submit.setUid("u-123");

        CMPPSubmitRepoMapUtil.put(sequence, submit);

        Assert.assertSame(submit, CMPPSubmitRepoMapUtil.get(sequence));
        Assert.assertSame(submit, CMPPSubmitRepoMapUtil.remove(sequence));
        Assert.assertNull(CMPPSubmitRepoMapUtil.get(sequence));
    }
}
