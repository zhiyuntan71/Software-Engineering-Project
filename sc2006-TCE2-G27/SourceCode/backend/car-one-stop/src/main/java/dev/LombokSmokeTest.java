package com.sc2006group5.car_one_stop.dev;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LombokSmokeTest {
    private String name;

    public static void main(String[] args) {
        LombokSmokeTest t = new LombokSmokeTest("test");
        System.out.println(t.getName()); // should compile if Lombok works
    }
}