package com.pserver.grpc.data;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;

public class GooglePayDataProcessor {

    public static void main(String[] args) throws IOException {
        new GooglePayDataProcessor().readFile("/Users/wangli/Downloads/salesreport_202510 2.csv");
    }

    private void readFile(String path) throws IOException {
        var data = Files.readAllLines(Paths.get(path));
        data.remove(0);
        data.stream().map(this::parseOrder).toList().forEach(System.out::println);
    }

    private Order parseOrder(String line) {
        String[] words = line.split(",");
        return new Order(words[0], Long.parseLong(words[2]), words[8], new BigDecimal(words[10]));
    }


    record Order(String orderNumber, long timestamp, String sku, BigDecimal price) {}
}
