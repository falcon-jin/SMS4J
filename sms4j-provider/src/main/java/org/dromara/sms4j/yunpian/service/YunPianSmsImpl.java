package org.dromara.sms4j.yunpian.service;

import com.alibaba.fastjson.JSONObject;
import com.dtflys.forest.config.ForestConfiguration;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.callback.CallBack;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.comm.annotation.Restricted;
import org.dromara.sms4j.comm.constant.Constant;
import org.dromara.sms4j.comm.delayedTime.DelayedTime;
import org.dromara.sms4j.comm.exception.SmsBlendException;
import org.dromara.sms4j.yunpian.config.YunpianConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.dromara.sms4j.comm.utils.SmsUtil.listToString;

public class YunPianSmsImpl implements SmsBlend {

    private Executor pool;

    private DelayedTime delayed;

    private YunpianConfig config;

    private ForestConfiguration http;

    private static SmsResponse getSmsResponse(JSONObject execute) {
        SmsResponse smsResponse = new SmsResponse();
        smsResponse.setCode(execute.getString("code"));
        smsResponse.setMessage(execute.getString("msg"));
        smsResponse.setBizId(execute.getString("sid"));
        if (execute.getInteger("code") != 0) {
            smsResponse.setErrMessage(execute.getString("msg"));
        }
        smsResponse.setData(execute);
        return smsResponse;
    }

    @Override
    @Restricted
    public SmsResponse sendMessage(String phone, String message) {
        Map<String, String> body = setBody(phone, message, null, config.getTemplateId());
        return getSendResponse(body);
    }

    @Override
    @Restricted
    public SmsResponse sendMessage(String phone, String templateId, LinkedHashMap<String, String> messages) {
        Map<String, String> body = setBody(phone, "", messages,templateId);
        return getSendResponse(body);
    }

    @Override
    @Restricted
    public SmsResponse massTexting(List<String> phones, String message) {
        if (phones.size() > 1000) {
            throw new SmsBlendException("单次发送超过最大发送上限，建议每次群发短信人数低于1000");
        }
        return sendMessage(listToString(phones), message);
    }

    @Override
    @Restricted
    public SmsResponse massTexting(List<String> phones, String templateId, LinkedHashMap<String, String> messages) {
        if (phones.size() > 1000) {
            throw new SmsBlendException("单次发送超过最大发送上限，建议每次群发短信人数低于1000");
        }
        return sendMessage(listToString(phones), templateId, messages);
    }

    @Override
    @Restricted
    public void sendMessageAsync(String phone, String message, CallBack callBack) {
        CompletableFuture<SmsResponse> smsResponseCompletableFuture = CompletableFuture.supplyAsync(() -> sendMessage(phone, message), pool);
        smsResponseCompletableFuture.thenAcceptAsync(callBack::callBack);
    }

    @Override
    @Restricted
    public void sendMessageAsync(String phone, String message) {
        pool.execute(() -> sendMessage(phone, message));
    }

    @Override
    @Restricted
    public void sendMessageAsync(String phone, String templateId, LinkedHashMap<String, String> messages, CallBack callBack) {
        CompletableFuture<SmsResponse> smsResponseCompletableFuture = CompletableFuture.supplyAsync(() -> sendMessage(phone, templateId, messages), pool);
        smsResponseCompletableFuture.thenAcceptAsync(callBack::callBack);
    }

    @Override
    @Restricted
    public void sendMessageAsync(String phone, String templateId, LinkedHashMap<String, String> messages) {
        pool.execute(() -> {
            sendMessage(phone, templateId, messages);
        });
    }

    @Override
    @Restricted
    public void delayedMessage(String phone, String message, Long delayedTime) {
        this.delayed.schedule(new TimerTask() {
            @Override
            public void run() {
                sendMessage(phone, message);
            }
        }, delayedTime);
    }

    @Override
    @Restricted
    public void delayedMessage(String phone, String templateId, LinkedHashMap<String, String> messages, Long delayedTime) {
        this.delayed.schedule(new TimerTask() {
            @Override
            public void run() {
                sendMessage(phone, templateId, messages);
            }
        }, delayedTime);
    }

    @Override
    @Restricted
    public void delayMassTexting(List<String> phones, String message, Long delayedTime) {
        this.delayed.schedule(new TimerTask() {
            @Override
            public void run() {
                massTexting(phones, message);
            }
        }, delayedTime);
    }

    @Override
    @Restricted
    public void delayMassTexting(List<String> phones, String templateId, LinkedHashMap<String, String> messages, Long delayedTime) {
        this.delayed.schedule(new TimerTask() {
            @Override
            public void run() {
                massTexting(phones, templateId, messages);
            }
        }, delayedTime);
    }

    private String formattingMap(Map<String, String> messages) {
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            str.append("#");
            str.append(entry.getKey());
            str.append("#=");
            str.append(entry.getValue());
            str.append("&");
        }
        str.deleteCharAt(str.length() - 1);
        return str.toString();
    }

    private Map<String, String> setBody(String phone, String mes, LinkedHashMap<String, String> messages, String tplId) {
        LinkedHashMap<String, String> message = new LinkedHashMap<>();
        if (mes.isEmpty()) {
            message = messages;
        } else {
            message.put(config.getTemplateName(), mes);
        }
        Map<String, String> body = new HashMap<>();
        body.put("apikey", config.getApikey());
        body.put("mobile", phone);
        body.put("tpl_id", tplId);
        body.put("tpl_value", formattingMap(message));
        if (!config.getCallbackUrl().isEmpty()) body.put("callback_url", config.getCallbackUrl());
        return body;
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json;charset=utf-8");
        headers.put("Content-Type", Constant.FROM_URLENCODED);
        return headers;
    }

    private SmsResponse getSendResponse(Map<String, String> body) {
        Map<String, String> headers = getHeaders();
        AtomicReference<SmsResponse> smsResponse = null;
        http.post(Constant.YUNPIAN_URL + "/sms/tpl_single_send.json")
                .addHeader(headers)
                .addBody(body)
                .onSuccess(((data, req, res) -> {
                    JSONObject jsonBody = res.get(JSONObject.class);
                    smsResponse.set(getSmsResponse(jsonBody));
                }))
                .onError((ex, req, res) -> {
                    JSONObject jsonBody = res.get(JSONObject.class);
                    smsResponse.set(getSmsResponse(jsonBody));
                })
                .execute();

        return smsResponse.get();
    }
}
