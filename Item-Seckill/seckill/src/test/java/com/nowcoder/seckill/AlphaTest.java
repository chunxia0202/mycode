package com.nowcoder.seckill;

import com.wf.captcha.ArithmeticCaptcha;
import com.wf.captcha.SpecCaptcha;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
//生成验证码的测试方法
public class AlphaTest {

    public static void main(String[] args) throws IOException {
        File file = new File("D:/temp/captcha.png");
        FileOutputStream outputStream = new FileOutputStream(file);
        //生成简单的四位英文字符
        SpecCaptcha specCaptcha = new SpecCaptcha(130, 48, 4);
        specCaptcha.out(outputStream);
        System.out.println(specCaptcha.text());
    }

}
