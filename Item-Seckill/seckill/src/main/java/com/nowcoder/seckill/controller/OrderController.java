package com.nowcoder.seckill.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.nowcoder.seckill.common.BusinessException;
import com.nowcoder.seckill.common.ErrorCode;
import com.nowcoder.seckill.common.ResponseModel;
import com.nowcoder.seckill.entity.User;
import com.nowcoder.seckill.service.OrderService;
import com.nowcoder.seckill.service.PromotionService;
import com.wf.captcha.SpecCaptcha;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/order")
@CrossOrigin(origins = "${nowcoder.web.path}", allowedHeaders = "*", allowCredentials = "true")
public class OrderController implements ErrorCode {

    private Logger logger = LoggerFactory.getLogger(OrderController.class);
    //限流器，单机每秒处理1000个请求
    private RateLimiter rateLimiter = RateLimiter.create(1000);

    @Autowired
    private OrderService orderService;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;//spring提供的线程池的封装,直接注入线程池对象
    //获取验证码，获取验证码的前提是登陆，所以要先获取登陆的token
    @RequestMapping(path = "/captcha", method = RequestMethod.GET)
    public void getCaptcha(String token, HttpServletResponse response) {
        SpecCaptcha specCaptcha = new SpecCaptcha(130, 48, 4);

        if (token != null) {
            User user = (User) redisTemplate.opsForValue().get(token);
            if (user != null) {
                //用户登陆状态下将验证码与用户对应的id存入redis缓存中，方便后续判断写入的验证码是否正确
                String key = "captcha:" + user.getId();
                //一分钟过期
                redisTemplate.opsForValue().set(key, specCaptcha.text(), 1, TimeUnit.MINUTES);
            }
        }
        //将验证码的内容进行响应，响应格式为png的图片格式；
        response.setContentType("image/png");
        try {
            OutputStream os = response.getOutputStream();//响应获取输出流
            specCaptcha.out(os);//应用输出流向客户端输出图片
        } catch (IOException e) {
            logger.error("发送验证码失败：" + e.getMessage());
        }
    }
    //带着验证码与用户的登陆状态来请求token
    @RequestMapping(path = "/token", method = RequestMethod.POST)
    @ResponseBody
    public ResponseModel generateToken(int itemId, int promotionId, String token, String captcha) {
        User user = (User) redisTemplate.opsForValue().get(token);

        // 判断是否为空
        if (StringUtils.isEmpty(captcha)) {
            throw new BusinessException(PARAMETER_ERROR, "请输入正确的验证码！");
        }

        // 判断验证码是否正确
        String key = "captcha:" + user.getId();
        String realCaptcha = (String) redisTemplate.opsForValue().get(key);
        //equalsIgnoreCase不区分大小写
        if (!captcha.equalsIgnoreCase(realCaptcha)) {
            throw new BusinessException(PARAMETER_ERROR, "请输入正确的验证码！");
        }

        // 生成令牌
        String promotionToken = promotionService.generateToken(user.getId(), itemId, promotionId);
        if (StringUtils.isEmpty(promotionToken)) {
            throw new BusinessException(CREATE_ORDER_FAILURE, "下单失败！");
        }
        return new ResponseModel(promotionToken);
    }

    @RequestMapping(path = "/create", method = RequestMethod.POST)
    @ResponseBody
    //token是用户的登陆凭证，promotionToken是参与活动的凭证
    public ResponseModel create(/*HttpSession session, */
            int itemId, int amount, Integer promotionId, String promotionToken, String token) {
          //最后一个token是前端传来的
//        User user = (User) session.getAttribute("loginUser");
          //限制单机流量
          //申请1s之内能否抢到单机的令牌，能抢到就可以继续操作
          //若TPS已经到极限就输出服务器繁忙。。。
        if (!rateLimiter.tryAcquire(1,TimeUnit.SECONDS)) {
           throw new BusinessException(OUT_OF_LIMIT, "服务器繁忙，请稍后再试！");
        }

        User user = (User) redisTemplate.opsForValue().get(token);
        logger.debug("登录用户 [" + token + ": " + user + "]");

        if (promotionId != null) {
            String key = "promotion:token:" + user.getId() + ":" + itemId + ":" + promotionId;
            String realPromotionToken = (String) redisTemplate.opsForValue().get(key);
            if (StringUtils.isEmpty(promotionToken) || !promotionToken.equals(realPromotionToken)) {
                throw new BusinessException(CREATE_ORDER_FAILURE, "下单失败！");
            }
        }
        // 加入队列等待
        //线程池提交callable方法执行多线程的方法
        //submit提交需要返回值的事务，返回的类型为future,可以判断事务是否执行成功
        Future future = taskExecutor.submit(new Callable() {
            @Override
            public Object call() throws Exception {
//              orderService.createOrder(user.getId(), itemId, amount, promotionId);
                //生产者向broker server发送消息，生成流水的方法createOrderAsync
                orderService.createOrderAsync(user.getId(), itemId, amount, promotionId);
                return null;
            }
        });
        try {
            future.get();//返回验证结果
        } catch (Exception e) {
            throw new BusinessException(UNDEFINED_ERROR, "下单失败！");
        }
        return new ResponseModel();//若中间地方没有输出false,那么创建订单的返回状态就是success
    }
}
