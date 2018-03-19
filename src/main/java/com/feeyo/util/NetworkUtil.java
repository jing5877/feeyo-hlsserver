package com.feeyo.util;

import java.io.IOException;


/**
 * TODO brief desc
 *
 * @author Tr!bf wangyamin@variflight.com
 */
public class NetworkUtil {
    public static String getDefaultRouteIf() throws IOException {
        return ShellUtils.execCommand("bash", "-c", "route -n |grep '^0.0.0.0' | awk  'BEGIN{ORS=\"\"} NR==1 {print $8}'");
    }

    public static void main(String[] args) throws IOException {
        System.out.println(getDefaultRouteIf());
    }
}
