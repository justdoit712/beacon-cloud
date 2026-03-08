package com.cz.api.T;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class t {
    public static void main(String[] args) {
        int[] nums1 = {-3, 2, 8, 15};
        int[] nums2 = {1, 5, 7, 9, 12, 20, 25};

        int[] res = merge(nums1,nums2);
        for (int i : res) {
            System.out.println(i);
        }
    }

    public static String largestNumber(int[] nums) {
        // 1. 将 int 数组转换为 String 数组
        String[] strs = new String[nums.length];
        for (int i = 0; i < nums.length; i++) {
            strs[i] = String.valueOf(nums[i]);
        }

        Arrays.sort(strs, (s1, s2) -> (s2 + s1).compareTo(s1 + s2));

        StringBuilder sb = new StringBuilder();
        for (String str : strs) {
            sb.append(str);
        }

        return sb.toString();
    }

    public static int[] merge(int[] nums1,int[] nums2){
        int i = 0,j = 0;
        int[] res = new int[nums1.length + nums2.length];
        while  (i < nums1.length && j < nums2.length){
            if(nums1[i] > nums2[j]){
                res[i+j] = nums2[j];
                j++;
            } else if (nums1[i] < nums2[j]) {
                res[i+j] = nums1[i];
                i++;
            }else {
                res[i + j] = nums1[i];
                res[i + j + 1] = nums1[i];
                i++;
                j++;
            }
        }

        if (i == nums1.length) {
            System.arraycopy(nums2, j, res, i + j, nums2.length - j);
        } else {
            System.arraycopy(nums1, i, res, i + j, nums1.length - i);
        }
        return res;
    }


}
