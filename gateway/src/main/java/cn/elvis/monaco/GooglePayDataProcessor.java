package cn.elvis.monaco;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GooglePayDataProcessor {

  public static void main(String[] args) throws IOException {
    new GooglePayDataProcessor().findSubtract();
  }

  private void findSubtract() throws IOException {
    var googleData = parseGooglePlayData("/Users/wangli/Downloads/salesreport_202511 3.csv");
    System.out.println(googleData.size() + "==============");
    var dbOrderIdList = parseDatabaseData("/Users/wangli/Result_1.csv");
    var dbOrders =
        parseDatabaseOrder("/Users/wangli/Result_1.csv").stream()
            .collect(Collectors.toMap(Order::orderNumber, order -> order));
    var dbTotalAmount = dbOrders.values().stream().map(Order::price).reduce(BigDecimal::add).get();
    int total = 0;
    BigDecimal totalDiscardAmount = new BigDecimal(0);
    BigDecimal googleTotalAmount = new BigDecimal(0);
    for (Order gOrder : googleData) {
        if (!dbOrderIdList.contains(gOrder.orderNumber)) {
                if (gOrder.sku.contains("coins")) {
                    System.out.println(gOrder.orderNumber);
                }

            totalDiscardAmount = totalDiscardAmount.add(gOrder.price);
            total++;
        } else {
            var dbOrder = dbOrders.get(gOrder.orderNumber);
            if (dbOrder.price.compareTo(gOrder.price) != 0) {
                System.out.println("exceptional order: " + gOrder.orderNumber + ", g_order_price: " + gOrder.price + ", db_order_price: " + dbOrder.price);
            }
        }
        googleTotalAmount = googleTotalAmount.add(gOrder.price);
    }



    System.out.println("total: " + total + " totalDiscardAmount: " + totalDiscardAmount + " googleTotalAmount: " + googleTotalAmount + " dbTotalAmount: " + dbTotalAmount);
  }

  private List<String> parseDatabaseData(String path) throws IOException {
    var data = Files.readAllLines(Paths.get(path));
    return data.stream()
        .map(
            l -> {
              var words = l.split(",");
              return words[0];
            })
        .toList();
  }

  private List<Order> parseDatabaseOrder(String path) throws IOException {
    var data = Files.readAllLines(Paths.get(path));
    return data.stream()
        .map(
            l -> {
              var words = l.split(",");
              return new Order(
                  words[0], words[1].substring(0, 10), words[2], new BigDecimal(words[4]));
            })
        .toList();
  }

  private List<Order> parseGooglePlayData(String path) throws IOException {
    var data = Files.readAllLines(Paths.get(path));
    data.remove(0);
    return data.stream()
        .map(this::parseOrder)
        .filter(o -> List.of("2025-11-02").contains(o.date))
        .toList();
  }

  private Order parseOrder(String line) {
    String[] words = line.split(",");
    return new Order(words[0], words[1], words[8], new BigDecimal(words[10]));
  }

  record Order(String orderNumber, String date, String sku, BigDecimal price) {}
}
