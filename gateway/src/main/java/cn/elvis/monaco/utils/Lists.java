package cn.elvis.monaco.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * List utilities
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class Lists {

    public static <T> List<T> repeat(T e, int times) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            list.add(e);
        }
        return list;
    }
}
