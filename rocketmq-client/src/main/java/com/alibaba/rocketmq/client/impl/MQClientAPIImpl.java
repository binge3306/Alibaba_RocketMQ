/**
 * Copyright (C) 2010-2013 Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.rocketmq.client.impl;

import com.alibaba.rocketmq.client.VirtualEnvUtil;
import com.alibaba.rocketmq.client.consumer.PullCallback;
import com.alibaba.rocketmq.client.consumer.PullResult;
import com.alibaba.rocketmq.client.consumer.PullStatus;
import com.alibaba.rocketmq.client.exception.MQBrokerException;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.client.hook.SendMessageContext;
import com.alibaba.rocketmq.client.impl.consumer.PullResultExt;
import com.alibaba.rocketmq.client.impl.factory.MQClientInstance;
import com.alibaba.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import com.alibaba.rocketmq.client.impl.producer.TopicPublishInfo;
import com.alibaba.rocketmq.client.log.ClientLogger;
import com.alibaba.rocketmq.client.producer.SendCallback;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.client.producer.SendStatus;
import com.alibaba.rocketmq.common.MQVersion;
import com.alibaba.rocketmq.common.MixAll;
import com.alibaba.rocketmq.common.TopicConfig;
import com.alibaba.rocketmq.common.UtilAll;
import com.alibaba.rocketmq.common.admin.ConsumeStats;
import com.alibaba.rocketmq.common.admin.OffsetWrapper;
import com.alibaba.rocketmq.common.admin.TopicOffset;
import com.alibaba.rocketmq.common.admin.TopicStatsTable;
import com.alibaba.rocketmq.common.message.Message;
import com.alibaba.rocketmq.common.message.MessageDecoder;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.alibaba.rocketmq.common.message.MessageQueue;
import com.alibaba.rocketmq.common.namesrv.NamesrvUtil;
import com.alibaba.rocketmq.common.namesrv.TopAddressing;
import com.alibaba.rocketmq.common.protocol.RequestCode;
import com.alibaba.rocketmq.common.protocol.ResponseCode;
import com.alibaba.rocketmq.common.protocol.body.BrokerStatsData;
import com.alibaba.rocketmq.common.protocol.body.ClusterInfo;
import com.alibaba.rocketmq.common.protocol.body.ConsumeMessageDirectlyResult;
import com.alibaba.rocketmq.common.protocol.body.ConsumerConnection;
import com.alibaba.rocketmq.common.protocol.body.ConsumerRunningInfo;
import com.alibaba.rocketmq.common.protocol.body.GetConsumerStatusBody;
import com.alibaba.rocketmq.common.protocol.body.GroupList;
import com.alibaba.rocketmq.common.protocol.body.KVTable;
import com.alibaba.rocketmq.common.protocol.body.LockBatchRequestBody;
import com.alibaba.rocketmq.common.protocol.body.LockBatchResponseBody;
import com.alibaba.rocketmq.common.protocol.body.ProducerConnection;
import com.alibaba.rocketmq.common.protocol.body.QueryConsumeTimeSpanBody;
import com.alibaba.rocketmq.common.protocol.body.QueryCorrectionOffsetBody;
import com.alibaba.rocketmq.common.protocol.body.QueueTimeSpan;
import com.alibaba.rocketmq.common.protocol.body.ResetOffsetBody;
import com.alibaba.rocketmq.common.protocol.body.SubscriptionGroupWrapper;
import com.alibaba.rocketmq.common.protocol.body.TopicList;
import com.alibaba.rocketmq.common.protocol.body.UnlockBatchRequestBody;
import com.alibaba.rocketmq.common.protocol.header.AddCommitLogStorePathRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.CloneGroupOffsetRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.ConsumeMessageDirectlyResultRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.ConsumerSendMsgBackRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.CreateTopicRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.DeleteSubscriptionGroupRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.DeleteTopicRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.EndTransactionRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetConsumeStatsRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetConsumerConnectionListRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetConsumerListByGroupRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetConsumerListByGroupResponseBody;
import com.alibaba.rocketmq.common.protocol.header.GetConsumerRunningInfoRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetConsumerStatusRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetEarliestMsgStoretimeRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetEarliestMsgStoretimeResponseHeader;
import com.alibaba.rocketmq.common.protocol.header.GetMaxOffsetRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetMaxOffsetResponseHeader;
import com.alibaba.rocketmq.common.protocol.header.GetMinOffsetRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetMinOffsetResponseHeader;
import com.alibaba.rocketmq.common.protocol.header.GetProducerConnectionListRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetTopicStatsInfoRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.GetTopicsByClusterRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.PullMessageRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.PullMessageResponseHeader;
import com.alibaba.rocketmq.common.protocol.header.QueryConsumeTimeSpanRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.QueryConsumerOffsetRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.QueryConsumerOffsetResponseHeader;
import com.alibaba.rocketmq.common.protocol.header.QueryCorrectionOffsetHeader;
import com.alibaba.rocketmq.common.protocol.header.QueryMessageRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.QueryTopicConsumeByWhoRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.ResetOffsetRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.SearchOffsetRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.SearchOffsetResponseHeader;
import com.alibaba.rocketmq.common.protocol.header.SendMessageRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.SendMessageRequestHeaderV2;
import com.alibaba.rocketmq.common.protocol.header.SendMessageResponseHeader;
import com.alibaba.rocketmq.common.protocol.header.UnregisterClientRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.UpdateConsumerOffsetRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.ViewBrokerStatsDataRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.ViewMessageRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.filtersrv.RegisterMessageFilterClassRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.namesrv.DeleteKVConfigRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.namesrv.DeleteTopicInNamesrvRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.namesrv.GetKVConfigRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.namesrv.GetKVConfigResponseHeader;
import com.alibaba.rocketmq.common.protocol.header.namesrv.GetKVListByNamespaceRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.namesrv.GetRouteInfoRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.namesrv.PutKVConfigRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.namesrv.WipeWritePermOfBrokerRequestHeader;
import com.alibaba.rocketmq.common.protocol.header.namesrv.WipeWritePermOfBrokerResponseHeader;
import com.alibaba.rocketmq.common.protocol.heartbeat.ConsumerData;
import com.alibaba.rocketmq.common.protocol.heartbeat.HeartbeatData;
import com.alibaba.rocketmq.common.protocol.heartbeat.ProducerData;
import com.alibaba.rocketmq.common.protocol.heartbeat.SubscriptionData;
import com.alibaba.rocketmq.common.protocol.route.TopicRouteData;
import com.alibaba.rocketmq.common.subscription.SubscriptionGroupConfig;
import com.alibaba.rocketmq.common.sysflag.MessageSysFlag;
import com.alibaba.rocketmq.remoting.InvokeCallback;
import com.alibaba.rocketmq.remoting.RPCHook;
import com.alibaba.rocketmq.remoting.RemotingClient;
import com.alibaba.rocketmq.remoting.common.RemotingUtil;
import com.alibaba.rocketmq.remoting.exception.RemotingCommandException;
import com.alibaba.rocketmq.remoting.exception.RemotingConnectException;
import com.alibaba.rocketmq.remoting.exception.RemotingException;
import com.alibaba.rocketmq.remoting.exception.RemotingSendRequestException;
import com.alibaba.rocketmq.remoting.exception.RemotingTimeoutException;
import com.alibaba.rocketmq.remoting.exception.RemotingTooMuchRequestException;
import com.alibaba.rocketmq.remoting.netty.NettyClientConfig;
import com.alibaba.rocketmq.remoting.netty.NettyRemotingClient;
import com.alibaba.rocketmq.remoting.netty.NettySystemConfig;
import com.alibaba.rocketmq.remoting.netty.ResponseFuture;
import com.alibaba.rocketmq.remoting.protocol.RemotingCommand;
import com.alibaba.rocketmq.remoting.protocol.RemotingSerializable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 封装所有与服务器通信部分API
 * 
 * @author shijia.wxr<vintage.wang@gmail.com>
 * @since 2013-7-24
 */
public class MQClientAPIImpl {

    static {
        System.setProperty(RemotingCommand.RemotingVersionKey, Integer.toString(MQVersion.CurrentVersion));
    }

    private final static Logger log = ClientLogger.getLog();

    /**
     * 消息压缩level，默认5
     */
    private int zipCompressLevel = Integer.parseInt(System.getProperty(MixAll.MESSAGE_COMPRESS_LEVEL, "5"));

    private final RemotingClient remotingClient;
    private final TopAddressing topAddressing = new TopAddressing(MixAll.WS_ADDR);
    private final ClientRemotingProcessor clientRemotingProcessor;
    private String nameSrvAddr = null;
    // 虚拟运行环境相关的project group
    private String projectGroupPrefix;


    public MQClientAPIImpl(final NettyClientConfig nettyClientConfig,
            final ClientRemotingProcessor clientRemotingProcessor, RPCHook rpcHook) {
        this.remotingClient = new NettyRemotingClient(nettyClientConfig, null);
        this.clientRemotingProcessor = clientRemotingProcessor;

        this.remotingClient.registerRPCHook(rpcHook);

        /*
         * 注册客户端支持的RPC CODE
         */
        this.remotingClient.registerProcessor(RequestCode.CHECK_TRANSACTION_STATE,
            this.clientRemotingProcessor, null);

        this.remotingClient.registerProcessor(RequestCode.NOTIFY_CONSUMER_IDS_CHANGED,
            this.clientRemotingProcessor, null);

        this.remotingClient.registerProcessor(RequestCode.RESET_CONSUMER_CLIENT_OFFSET,
            this.clientRemotingProcessor, null);

        this.remotingClient.registerProcessor(RequestCode.GET_CONSUMER_STATUS_FROM_CLIENT,
            this.clientRemotingProcessor, null);

        this.remotingClient.registerProcessor(RequestCode.GET_CONSUMER_RUNNING_INFO,
            this.clientRemotingProcessor, null);

        this.remotingClient.registerProcessor(RequestCode.CONSUME_MESSAGE_DIRECTLY,
            this.clientRemotingProcessor, null);
    }


    public MQClientAPIImpl(final NettyClientConfig nettyClientConfig,
            final ClientRemotingProcessor clientRemotingProcessor) {
        this(nettyClientConfig, clientRemotingProcessor, null);
    }


    public List<String> getNameServerAddressList() {
        return this.remotingClient.getNameServerAddressList();
    }


    public RemotingClient getRemotingClient() {
        return remotingClient;
    }


    public String fetchNameServerAddr() {
        log.info("Enter fetchNameServerAddr");
        try {
            String addrs = this.topAddressing.fetchNSAddr();
            if (addrs != null) {
                if (!addrs.equals(this.nameSrvAddr)) {
                    log.info("name server address changed, old: " + this.nameSrvAddr + " new: " + addrs);
                    this.updateNameServerAddressList(addrs);
                    this.nameSrvAddr = addrs;
                    return nameSrvAddr;
                }
            }
        } catch (Exception e) {
            log.error("fetchNameServerAddr Exception", e);
        }
        log.info("Exit fetchNameServerAddr, return {}", nameSrvAddr);
        return nameSrvAddr;
    }


    public void updateNameServerAddressList(final String addrs) {
        List<String> lst = new ArrayList<String>();
        String[] addrArray = addrs.split(";");
        Collections.addAll(lst, addrArray);

        this.remotingClient.updateNameServerAddressList(lst);
    }


    public void start() {
        // 远程通信 Client 启动
        this.remotingClient.start();

        // 获取虚拟运行环境相关的project group
        try {
            String localAddress = RemotingUtil.getLocalAddress();
            projectGroupPrefix = this.getProjectGroupByIp(localAddress, NettySystemConfig.NETTY_IO_TIMEOUT);
            log.info("The client[{}] in project group: {}", localAddress, projectGroupPrefix);
        }
        catch (Exception e) {
        }
    }


    public void shutdown() {
        this.remotingClient.shutdown();
    }


    public void createSubscriptionGroup(final String addr, final SubscriptionGroupConfig config,
            final long timeoutMillis) throws RemotingException, MQBrokerException, InterruptedException,
            MQClientException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            config.setGroupName(VirtualEnvUtil.buildWithProjectGroup(config.getGroupName(),
                projectGroupPrefix));
        }

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.UPDATE_AND_CREATE_SUBSCRIPTION_GROUP, null);

        byte[] body = RemotingSerializable.encode(config);
        request.setBody(body);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());

    }


    public void createTopic(final String addr, final String defaultTopic, final TopicConfig topicConfig,
            final long timeoutMillis) throws RemotingException, MQBrokerException, InterruptedException,
            MQClientException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String topicWithProjectGroup = topicConfig.getTopicName();
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            topicWithProjectGroup =
                    VirtualEnvUtil.buildWithProjectGroup(topicConfig.getTopicName(), projectGroupPrefix);
        }

        CreateTopicRequestHeader requestHeader = new CreateTopicRequestHeader();
        requestHeader.setTopic(topicWithProjectGroup);
        requestHeader.setDefaultTopic(defaultTopic);
        requestHeader.setReadQueueNums(topicConfig.getReadQueueNums());
        requestHeader.setWriteQueueNums(topicConfig.getWriteQueueNums());
        requestHeader.setPerm(topicConfig.getPerm());
        requestHeader.setTopicFilterType(topicConfig.getTopicFilterType().name());
        requestHeader.setTopicSysFlag(topicConfig.getTopicSysFlag());
        requestHeader.setOrder(topicConfig.isOrder());

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.UPDATE_AND_CREATE_TOPIC, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }

    /**
     * 是否发送网络包精简的Message
     */
    public static boolean sendSmartMsg = //
            Boolean.parseBoolean(System.getProperty("com.alibaba.rocketmq.client.sendSmartMsg", "false"));


    public SendResult sendMessage(//
                                  final String addr, // 1
                                  final String brokerName, // 2
                                  final Message msg, // 3
                                  final SendMessageRequestHeader requestHeader, // 4
                                  final long timeoutMillis, // 5
                                  final CommunicationMode communicationMode, // 6
                                  final SendMessageContext context, // 7
                                  final DefaultMQProducerImpl producer // 8
    ) throws RemotingException, MQBrokerException, InterruptedException {
        return sendMessage(addr, brokerName, msg, requestHeader, timeoutMillis, communicationMode, null, null, null, 0, context, producer);
    }

    /**
     * 发送消息
     */
    public SendResult sendMessage(//
                                  final String addr, // 1
                                  final String brokerName, // 2
                                  final Message msg, // 3
                                  final SendMessageRequestHeader requestHeader, // 4
                                  final long timeoutMillis, // 5
                                  final CommunicationMode communicationMode, // 6
                                  final SendCallback sendCallback, // 7
                                  final TopicPublishInfo topicPublishInfo, // 8
                                  final MQClientInstance instance, // 9
                                  final int retryTimesWhenSendFailed, // 10
                                  final SendMessageContext context, // 11
                                  final DefaultMQProducerImpl producer, // 12
                                  final boolean ignoreSemaphore
                                  ) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            msg.setTopic(VirtualEnvUtil.buildWithProjectGroup(msg.getTopic(), projectGroupPrefix));
            requestHeader.setProducerGroup(VirtualEnvUtil.buildWithProjectGroup(
                requestHeader.getProducerGroup(), projectGroupPrefix));
            requestHeader.setTopic(VirtualEnvUtil.buildWithProjectGroup(requestHeader.getTopic(),
                projectGroupPrefix));
        }

        byte[] msgBody = msg.getBody();

        // If message body exceeds compression threshold, try to compress it.
        if ((requestHeader.getSysFlag() & MessageSysFlag.CompressedFlag) == MessageSysFlag.CompressedFlag) {
            try {
                msgBody = UtilAll.compress(msgBody, zipCompressLevel);
            } catch (IOException e) {
                requestHeader.setSysFlag(MessageSysFlag.clearCompressedFlag(requestHeader.getSysFlag()));
                log.error("tryToCompressMessage exception", e);
                log.warn(msg.toString());
            }
        }

        RemotingCommand request = null;
        if (sendSmartMsg) {
            SendMessageRequestHeaderV2 requestHeaderV2 = SendMessageRequestHeaderV2.createSendMessageRequestHeaderV2(requestHeader);
            request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE_V2, requestHeaderV2);
        } else {
            request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, requestHeader);
        }

        request.setBody(msgBody);

        switch (communicationMode) {
            case ONEWAY:
                this.remotingClient.invokeOneway(addr, request, timeoutMillis);
                return null;
            case ASYNC:
                final AtomicInteger times = new AtomicInteger();
                this.sendMessageAsync(addr, brokerName, msg, timeoutMillis, request, requestHeader, sendCallback, topicPublishInfo,
                        instance, retryTimesWhenSendFailed, times, context, producer, ignoreSemaphore);
                return null;
            case SYNC:
                return this.sendMessageSync(addr, brokerName, msg, timeoutMillis, request);
            default:
                assert false;
                break;
        }

        return null;
    }

    public SendResult sendMessage(//
                                  final String addr, // 1
                                  final String brokerName, // 2
                                  final Message msg, // 3
                                  final SendMessageRequestHeader requestHeader, // 4
                                  final long timeoutMillis, // 5
                                  final CommunicationMode communicationMode, // 6
                                  final SendCallback sendCallback, // 7
                                  final TopicPublishInfo topicPublishInfo, // 8
                                  final MQClientInstance instance, // 9
                                  final int retryTimesWhenSendFailed, // 10
                                  final SendMessageContext context, // 11
                                  final DefaultMQProducerImpl producer // 12
    ) throws RemotingException, MQBrokerException, InterruptedException {
        return sendMessage(addr, brokerName, msg, requestHeader, timeoutMillis, communicationMode, sendCallback,
                topicPublishInfo, instance, retryTimesWhenSendFailed, context, producer, false);
    }


    private SendResult sendMessageSync(//
            final String addr,//
            final String brokerName,//
            final Message msg,//
            final long timeoutMillis,//
            final RemotingCommand request//
    ) throws RemotingException, MQBrokerException, InterruptedException {
        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        return this.processSendResponse(brokerName, msg, response);
    }


    private void sendMessageAsync(//
                                  final String addr, //
                                  final String brokerName, //
                                  final Message msg, //
                                  final long timeoutMillis, //
                                  final RemotingCommand request, //
                                  final SendMessageRequestHeader requestHeader, //
                                  final SendCallback sendCallback, //
                                  final TopicPublishInfo topicPublishInfo, //
                                  final MQClientInstance instance, //
                                  final int retryTimesWhenSendFailed, //
                                  final AtomicInteger times, //
                                  final SendMessageContext context, //
                                  final DefaultMQProducerImpl producer, //
                                  final boolean ignoreSemaphore
    ) throws InterruptedException, RemotingException {
        this.remotingClient.invokeAsync(addr, request, timeoutMillis, ignoreSemaphore, new InvokeCallback() {
            @Override
            public void operationComplete(ResponseFuture responseFuture) {
                RemotingCommand response = responseFuture.getResponseCommand();

                if (null == sendCallback && response != null) {

                    try {
                        SendResult sendResult = MQClientAPIImpl.this.processSendResponse(brokerName, msg, response);
                        if (context != null) {
                            context.setSendResult(sendResult);
                            context.getProducer().executeSendMessageHookAfter(context);
                        }
                    } catch (Throwable e) {
                        // TODO we need handle possible exception here and incur retry when applicable.
                    }

                    producer.updateFaultItem(brokerName, System.currentTimeMillis() - responseFuture.getBeginTimestamp(), false);
                    return;
                }

                if (response != null) {
                    try {
                        SendResult sendResult = MQClientAPIImpl.this.processSendResponse(brokerName, msg, response);
                        if (context != null) {
                            context.setSendResult(sendResult);
                            context.getProducer().executeSendMessageHookAfter(context);
                        }

                        try {
                            sendCallback.onSuccess(sendResult);
                        } catch (Throwable ignore) {
                        }

                        producer.updateFaultItem(brokerName, System.currentTimeMillis() - responseFuture.getBeginTimestamp(), false);
                    } catch (Exception e) {
                        producer.updateFaultItem(brokerName, System.currentTimeMillis() - responseFuture.getBeginTimestamp(), true);
                        onExceptionImpl(brokerName, msg, 0L, request, requestHeader, sendCallback, topicPublishInfo, instance,
                                retryTimesWhenSendFailed, times, e, context, false, producer, ignoreSemaphore);
                    }
                } else {
                    producer.updateFaultItem(brokerName, System.currentTimeMillis() - responseFuture.getBeginTimestamp(), true);
                    if (!responseFuture.isSendRequestOK()) {
                        MQClientException ex = new MQClientException("send request failed", responseFuture.getCause());
                        onExceptionImpl(brokerName, msg, 0L, request, requestHeader, sendCallback, topicPublishInfo, instance,
                                retryTimesWhenSendFailed, times, ex, context, true, producer, ignoreSemaphore);
                    } else if (responseFuture.isTimeout()) {
                        MQClientException ex = new MQClientException("wait response timeout " + responseFuture.getTimeoutMillis() + "ms",
                                responseFuture.getCause());
                        onExceptionImpl(brokerName, msg, 0L, request, requestHeader, sendCallback, topicPublishInfo, instance,
                                retryTimesWhenSendFailed, times, ex, context, true, producer, ignoreSemaphore);
                    } else {
                        MQClientException ex = new MQClientException("unknown reason", responseFuture.getCause());
                        onExceptionImpl(brokerName, msg, 0L, request, requestHeader, sendCallback, topicPublishInfo, instance,
                                retryTimesWhenSendFailed, times, ex, context, true, producer, ignoreSemaphore);
                    }
                }
            }
        });
    }

    private void onExceptionImpl(final String brokerName, //
                                 final Message msg, //
                                 final long timeoutMillis, //
                                 final RemotingCommand request, //
                                 final SendMessageRequestHeader requestHeader, //
                                 final SendCallback sendCallback, //
                                 final TopicPublishInfo topicPublishInfo, //
                                 final MQClientInstance instance, //
                                 final int totalRetryTimes, //
                                 final AtomicInteger curTimes, //
                                 final Exception e, //
                                 final SendMessageContext context, //
                                 final boolean needRetry, //
                                 final DefaultMQProducerImpl producer, // 12
                                 final boolean ignoreSemaphore
    ) {
        int tmp = curTimes.incrementAndGet();
        if (needRetry && tmp <= totalRetryTimes) {

            //by default, it will send to the same broker
            String retryBrokerName = brokerName;

            //select one message queue accordingly, in order to determine which broker to send
            if (topicPublishInfo != null) {
                MessageQueue mqChosen = producer.selectOneMessageQueue(topicPublishInfo, brokerName);
                retryBrokerName = mqChosen.getBrokerName();
            }
            String addr = instance.findBrokerAddressInPublish(retryBrokerName);

            log.info("async send msg by retry {} times. topic={}, brokerAddr={}, brokerName={}", tmp, msg.getTopic(),
                    addr, retryBrokerName);
            try {
                request.setOpaque(RemotingCommand.createNewRequestId());
                sendMessageAsync(addr, retryBrokerName, msg, timeoutMillis, request, requestHeader, sendCallback,
                        topicPublishInfo, instance, totalRetryTimes, curTimes, context, producer, ignoreSemaphore);
            } catch (InterruptedException exception) {
                onExceptionImpl(retryBrokerName, msg, timeoutMillis, request, requestHeader, sendCallback, topicPublishInfo,
                        instance, totalRetryTimes, curTimes, exception, context, false, producer, ignoreSemaphore);
            } catch (RemotingConnectException exception) {
                producer.updateFaultItem(brokerName, 3000, true);
                onExceptionImpl(retryBrokerName, msg, timeoutMillis, request, requestHeader, sendCallback, topicPublishInfo,
                        instance, totalRetryTimes, curTimes, exception, context, true, producer, ignoreSemaphore);
            } catch (RemotingTooMuchRequestException exception) {
                onExceptionImpl(retryBrokerName, msg, timeoutMillis, request, requestHeader, sendCallback, topicPublishInfo,
                        instance, totalRetryTimes, curTimes, exception, context, false, producer, ignoreSemaphore);
            } catch (RemotingException exception) {
                producer.updateFaultItem(brokerName, 3000, true);
                onExceptionImpl(retryBrokerName, msg, timeoutMillis, request, requestHeader, sendCallback, topicPublishInfo,
                        instance, totalRetryTimes, curTimes, exception, context, true, producer, ignoreSemaphore);
            }
        } else {
            if (context != null) {
                context.setException(e);
                context.getProducer().executeSendMessageHookAfter(context);
            }

            try {
                sendCallback.onException(e);
            } catch (Exception ignore) {
            }
        }
    }


    private SendResult processSendResponse(//
                                           final String brokerName, //
                                           final Message msg, //
                                           final RemotingCommand response//
    ) throws MQBrokerException, RemotingCommandException {
        switch (response.getCode()) {
            case ResponseCode.FLUSH_DISK_TIMEOUT:
            case ResponseCode.FLUSH_SLAVE_TIMEOUT:
            case ResponseCode.SLAVE_NOT_AVAILABLE: {
                // TODO LOG
            }
            case ResponseCode.SUCCESS: {
                SendStatus sendStatus = SendStatus.SEND_OK;
                switch (response.getCode()) {
                    case ResponseCode.FLUSH_DISK_TIMEOUT:
                        sendStatus = SendStatus.FLUSH_DISK_TIMEOUT;
                        break;
                    case ResponseCode.FLUSH_SLAVE_TIMEOUT:
                        sendStatus = SendStatus.FLUSH_SLAVE_TIMEOUT;
                        break;
                    case ResponseCode.SLAVE_NOT_AVAILABLE:
                        sendStatus = SendStatus.SLAVE_NOT_AVAILABLE;
                        break;
                    case ResponseCode.SUCCESS:
                        sendStatus = SendStatus.SEND_OK;
                        break;
                    default:
                        assert false;
                        break;
                }

            SendMessageResponseHeader responseHeader =
                    (SendMessageResponseHeader)response.decodeCommandCustomHeader(SendMessageResponseHeader.class);
            MessageQueue messageQueue = new MessageQueue(msg.getTopic(), brokerName, responseHeader.getQueueId());
            return new SendResult(sendStatus, responseHeader.getMsgId(), messageQueue, responseHeader.getQueueOffset(),
                    projectGroupPrefix, responseHeader.getKeys());
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 拉消息接口
     */
    public PullResult pullMessage(//
                                  final String addr, //
                                  final PullMessageRequestHeader requestHeader, //
                                  final long timeoutMillis, //
                                  final CommunicationMode communicationMode, //
                                  final PullCallback pullCallback//
    ) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            requestHeader.setConsumerGroup(VirtualEnvUtil.buildWithProjectGroup(
                requestHeader.getConsumerGroup(), projectGroupPrefix));
            requestHeader.setTopic(VirtualEnvUtil.buildWithProjectGroup(requestHeader.getTopic(),
                projectGroupPrefix));
        }

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.PULL_MESSAGE, requestHeader);

        switch (communicationMode) {
        case ONEWAY:
            assert false;
            return null;
        case ASYNC:
            this.pullMessageAsync(addr, request, timeoutMillis, pullCallback);
            return null;
        case SYNC:
            return this.pullMessageSync(addr, request, timeoutMillis);
        default:
            assert false;
            break;
        }

        return null;
    }


    private void pullMessageAsync(//
                                  final String addr, // 1
                                  final RemotingCommand request, //
                                  final long timeoutMillis, //
                                  final PullCallback pullCallback//
    ) throws RemotingException, InterruptedException {
        this.remotingClient.invokeAsync(addr, request, timeoutMillis, new InvokeCallback() {
            @Override
            public void operationComplete(ResponseFuture responseFuture) {
                RemotingCommand response = responseFuture.getResponseCommand();
                if (response != null) {
                    try {
                        PullResult pullResult = MQClientAPIImpl.this.processPullResponse(response);
                        pullCallback.onSuccess(pullResult);
                    }
                    catch (Exception e) {
                        pullCallback.onException(e);
                    }
                } else {
                    if (!responseFuture.isSendRequestOK()) {
                        pullCallback.onException(new MQClientException("send request failed", responseFuture
                            .getCause()));
                    }
                    else if (responseFuture.isTimeout()) {
                        pullCallback.onException(new MQClientException("wait response timeout "
                                + responseFuture.getTimeoutMillis() + "ms", responseFuture.getCause()));
                    }
                    else {
                        pullCallback.onException(new MQClientException("unknown reason", responseFuture
                            .getCause()));
                    }
                }
            }
        });
    }


    private PullResult processPullResponse(final RemotingCommand response) throws MQBrokerException,
            RemotingCommandException {
        PullStatus pullStatus = PullStatus.NO_NEW_MSG;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS:
            pullStatus = PullStatus.FOUND;
            break;
        case ResponseCode.PULL_NOT_FOUND:
            pullStatus = PullStatus.NO_NEW_MSG;
            break;
        case ResponseCode.PULL_RETRY_IMMEDIATELY:
            pullStatus = PullStatus.NO_MATCHED_MSG;
            break;
        case ResponseCode.PULL_OFFSET_MOVED:
            pullStatus = PullStatus.OFFSET_ILLEGAL;
            break;

        case ResponseCode.SLAVE_LAG_BEHIND:
            pullStatus = PullStatus.SLAVE_LAG_BEHIND;
            break;

        case ResponseCode.SUBSCRIPTION_NOT_LATEST:
            log.warn("Response Code: SUBSCRIPTION_NOT_LATEST. Remark: {}", response.getRemark());
            pullStatus = PullStatus.SUBSCRIPTION_NOT_LATEST;
            break;

        default:
            throw new MQBrokerException(response.getCode(), response.getRemark());
        }

        PullMessageResponseHeader responseHeader = (PullMessageResponseHeader) response.decodeCommandCustomHeader(PullMessageResponseHeader.class);

        return new PullResultExt(pullStatus, responseHeader.getNextBeginOffset(),
            responseHeader.getMinOffset(), responseHeader.getMaxOffset(), null,
            responseHeader.getSuggestWhichBrokerId(), response.getBody());
    }


    private PullResult pullMessageSync(//
            final String addr,// 1
            final RemotingCommand request,// 2
            final long timeoutMillis// 3
    ) throws RemotingException, InterruptedException, MQBrokerException {
        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        return this.processPullResponse(response);
    }


    /**
     * 根据时间查询Offset
     */
    public MessageExt viewMessage(final String addr, final long phyoffset, final long timeoutMillis)
            throws RemotingException, MQBrokerException, InterruptedException {
        ViewMessageRequestHeader requestHeader = new ViewMessageRequestHeader();
        requestHeader.setOffset(phyoffset);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.VIEW_MESSAGE_BY_ID, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            ByteBuffer byteBuffer = ByteBuffer.wrap(response.getBody());
            MessageExt messageExt = MessageDecoder.decode(byteBuffer);
            // 清除虚拟运行环境相关的projectGroupPrefix
            if (!UtilAll.isBlank(projectGroupPrefix)) {
                messageExt.setTopic(VirtualEnvUtil.clearProjectGroup(messageExt.getTopic(),
                    projectGroupPrefix));
            }
            return messageExt;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 根据时间查询Offset
     */
    public long searchOffset(final String addr, final String topic, final int queueId, final long timestamp,
            final long timeoutMillis) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String topicWithProjectGroup = topic;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            topicWithProjectGroup = VirtualEnvUtil.buildWithProjectGroup(topic, projectGroupPrefix);
        }

        SearchOffsetRequestHeader requestHeader = new SearchOffsetRequestHeader();
        requestHeader.setTopic(topicWithProjectGroup);
        requestHeader.setQueueId(queueId);
        requestHeader.setTimestamp(timestamp);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.SEARCH_OFFSET_BY_TIMESTAMP, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            SearchOffsetResponseHeader responseHeader =
                    (SearchOffsetResponseHeader) response
                        .decodeCommandCustomHeader(SearchOffsetResponseHeader.class);
            return responseHeader.getOffset();
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 获取队列的最大Offset
     */
    public long getMaxOffset(final String addr, final String topic, final int queueId,
            final long timeoutMillis) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String topicWithProjectGroup = topic;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            topicWithProjectGroup = VirtualEnvUtil.buildWithProjectGroup(topic, projectGroupPrefix);
        }

        GetMaxOffsetRequestHeader requestHeader = new GetMaxOffsetRequestHeader();
        requestHeader.setTopic(topicWithProjectGroup);
        requestHeader.setQueueId(queueId);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_MAX_OFFSET, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            GetMaxOffsetResponseHeader responseHeader =
                    (GetMaxOffsetResponseHeader) response
                        .decodeCommandCustomHeader(GetMaxOffsetResponseHeader.class);

            return responseHeader.getOffset();
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 获取某个组的Consumer Id列表
     */
    public List<String> getConsumerIdListByGroup(//
            final String addr, //
            final String consumerGroup, //
            final long timeoutMillis) throws RemotingConnectException, RemotingSendRequestException,
            RemotingTimeoutException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String consumerGroupWithProjectGroup = consumerGroup;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            consumerGroupWithProjectGroup =
                    VirtualEnvUtil.buildWithProjectGroup(consumerGroup, projectGroupPrefix);
        }

        GetConsumerListByGroupRequestHeader requestHeader = new GetConsumerListByGroupRequestHeader();
        requestHeader.setConsumerGroup(consumerGroupWithProjectGroup);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_CONSUMER_LIST_BY_GROUP, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            if (response.getBody() != null) {
                GetConsumerListByGroupResponseBody body =
                        GetConsumerListByGroupResponseBody.decode(response.getBody(),
                            GetConsumerListByGroupResponseBody.class);
                return body.getConsumerIdList();
            }
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 获取队列的最小Offset
     */
    public long getMinOffset(final String addr, final String topic, final int queueId,
            final long timeoutMillis) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String topicWithProjectGroup = topic;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            topicWithProjectGroup = VirtualEnvUtil.buildWithProjectGroup(topic, projectGroupPrefix);
        }

        GetMinOffsetRequestHeader requestHeader = new GetMinOffsetRequestHeader();
        requestHeader.setTopic(topicWithProjectGroup);
        requestHeader.setQueueId(queueId);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_MIN_OFFSET, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            GetMinOffsetResponseHeader responseHeader =
                    (GetMinOffsetResponseHeader) response
                        .decodeCommandCustomHeader(GetMinOffsetResponseHeader.class);

            return responseHeader.getOffset();
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 获取队列的最早时间
     */
    public long getEarliestMsgStoretime(final String addr, final String topic, final int queueId,
            final long timeoutMillis) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String topicWithProjectGroup = topic;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            topicWithProjectGroup = VirtualEnvUtil.buildWithProjectGroup(topic, projectGroupPrefix);
        }

        GetEarliestMsgStoretimeRequestHeader requestHeader = new GetEarliestMsgStoretimeRequestHeader();
        requestHeader.setTopic(topicWithProjectGroup);
        requestHeader.setQueueId(queueId);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_EARLIEST_MSG_STORETIME, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            GetEarliestMsgStoretimeResponseHeader responseHeader =
                    (GetEarliestMsgStoretimeResponseHeader) response
                        .decodeCommandCustomHeader(GetEarliestMsgStoretimeResponseHeader.class);

            return responseHeader.getTimestamp();
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 查询Consumer消费进度
     */
    public long queryConsumerOffset(//
            final String addr,//
            final QueryConsumerOffsetRequestHeader requestHeader,//
            final long timeoutMillis//
    ) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            requestHeader.setConsumerGroup(VirtualEnvUtil.buildWithProjectGroup(
                requestHeader.getConsumerGroup(), projectGroupPrefix));
            requestHeader.setTopic(VirtualEnvUtil.buildWithProjectGroup(requestHeader.getTopic(),
                projectGroupPrefix));
        }

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.QUERY_CONSUMER_OFFSET, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            QueryConsumerOffsetResponseHeader responseHeader =
                    (QueryConsumerOffsetResponseHeader) response
                        .decodeCommandCustomHeader(QueryConsumerOffsetResponseHeader.class);

            return responseHeader.getOffset();
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 更新Consumer消费进度
     */
    public void updateConsumerOffset(//
            final String addr,//
            final UpdateConsumerOffsetRequestHeader requestHeader,//
            final long timeoutMillis//
    ) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            requestHeader.setConsumerGroup(VirtualEnvUtil.buildWithProjectGroup(
                requestHeader.getConsumerGroup(), projectGroupPrefix));
            requestHeader.setTopic(VirtualEnvUtil.buildWithProjectGroup(requestHeader.getTopic(),
                projectGroupPrefix));
        }

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.UPDATE_CONSUMER_OFFSET, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 更新Consumer消费进度
     * 
     * @throws InterruptedException
     * @throws RemotingSendRequestException
     * @throws RemotingTimeoutException
     * @throws RemotingTooMuchRequestException
     * 
     * @throws RemotingConnectException
     */
    public void updateConsumerOffsetOneway(//
            final String addr,//
            final UpdateConsumerOffsetRequestHeader requestHeader,//
            final long timeoutMillis//
    ) throws RemotingConnectException, RemotingTooMuchRequestException, RemotingTimeoutException,
            RemotingSendRequestException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            requestHeader.setConsumerGroup(VirtualEnvUtil.buildWithProjectGroup(
                requestHeader.getConsumerGroup(), projectGroupPrefix));
            requestHeader.setTopic(VirtualEnvUtil.buildWithProjectGroup(requestHeader.getTopic(),
                projectGroupPrefix));
        }

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.UPDATE_CONSUMER_OFFSET, requestHeader);

        this.remotingClient.invokeOneway(addr, request, timeoutMillis);
    }


    /**
     * 发送心跳
     */
    public void sendHearbeat(//
            final String addr,//
            final HeartbeatData heartbeatData,//
            final long timeoutMillis//
    ) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            Set<ConsumerData> consumerDatas = heartbeatData.getConsumerDataSet();
            for (ConsumerData consumerData : consumerDatas) {
                consumerData.setGroupName(VirtualEnvUtil.buildWithProjectGroup(consumerData.getGroupName(),
                    projectGroupPrefix));
                Set<SubscriptionData> subscriptionDatas = consumerData.getSubscriptionDataSet();
                for (SubscriptionData subscriptionData : subscriptionDatas) {
                    subscriptionData.setTopic(VirtualEnvUtil.buildWithProjectGroup(
                        subscriptionData.getTopic(), projectGroupPrefix));
                }
            }
            Set<ProducerData> producerDatas = heartbeatData.getProducerDataSet();
            for (ProducerData producerData : producerDatas) {
                producerData.setGroupName(VirtualEnvUtil.buildWithProjectGroup(producerData.getGroupName(),
                    projectGroupPrefix));
            }
        }

        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.HEART_BEAT, null);

        request.setBody(heartbeatData.encode());
        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 发送心跳
     */
    public void unregisterClient(//
            final String addr,//
            final String clientID,//
            final String producerGroup,//
            final String consumerGroup,//
            final long timeoutMillis//
    ) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String producerGroupWithProjectGroup = producerGroup;
        String consumerGroupWithProjectGroup = consumerGroup;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            producerGroupWithProjectGroup =
                    VirtualEnvUtil.buildWithProjectGroup(producerGroup, projectGroupPrefix);
            consumerGroupWithProjectGroup =
                    VirtualEnvUtil.buildWithProjectGroup(consumerGroup, projectGroupPrefix);
        }

        final UnregisterClientRequestHeader requestHeader = new UnregisterClientRequestHeader();
        requestHeader.setClientID(clientID);
        requestHeader.setProducerGroup(producerGroupWithProjectGroup);
        requestHeader.setConsumerGroup(consumerGroupWithProjectGroup);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.UNREGISTER_CLIENT, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 提交或者回滚事务
     */
    public void endTransactionOneway(//
            final String addr,//
            final EndTransactionRequestHeader requestHeader,//
            final String remark,//
            final long timeoutMillis//
    ) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            requestHeader.setProducerGroup(VirtualEnvUtil.buildWithProjectGroup(
                requestHeader.getProducerGroup(), projectGroupPrefix));
        }

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.END_TRANSACTION, requestHeader);

        request.setRemark(remark);
        this.remotingClient.invokeOneway(addr, request, timeoutMillis);
    }


    /**
     * 查询消息
     */
    public void queryMessage(//
            final String addr,//
            final QueryMessageRequestHeader requestHeader,//
            final long timeoutMillis,//
            final InvokeCallback invokeCallback//
    ) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            requestHeader.setTopic(VirtualEnvUtil.buildWithProjectGroup(requestHeader.getTopic(),
                projectGroupPrefix));
        }

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.QUERY_MESSAGE, requestHeader);

        this.remotingClient.invokeAsync(addr, request, timeoutMillis, invokeCallback);
    }


    public boolean registerClient(final String addr, final HeartbeatData heartbeat, final long timeoutMillis)
            throws RemotingException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            Set<ConsumerData> consumerDatas = heartbeat.getConsumerDataSet();
            for (ConsumerData consumerData : consumerDatas) {
                consumerData.setGroupName(VirtualEnvUtil.buildWithProjectGroup(consumerData.getGroupName(),
                    projectGroupPrefix));
                Set<SubscriptionData> subscriptionDatas = consumerData.getSubscriptionDataSet();
                for (SubscriptionData subscriptionData : subscriptionDatas) {
                    subscriptionData.setTopic(VirtualEnvUtil.buildWithProjectGroup(
                        subscriptionData.getTopic(), projectGroupPrefix));
                }
            }
            Set<ProducerData> producerDatas = heartbeat.getProducerDataSet();
            for (ProducerData producerData : producerDatas) {
                producerData.setGroupName(VirtualEnvUtil.buildWithProjectGroup(producerData.getGroupName(),
                    projectGroupPrefix));
            }
        }

        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.HEART_BEAT, null);

        request.setBody(heartbeat.encode());
        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        return response.getCode() == ResponseCode.SUCCESS;
    }


    /**
     * 失败的消息发回Broker
     */
    public void consumerSendMessageBack(//
            final String addr, //
            final MessageExt msg,//
            final String consumerGroup,//
            final int delayLevel,//
            final long timeoutMillis//
    ) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String consumerGroupWithProjectGroup = consumerGroup;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            consumerGroupWithProjectGroup =
                    VirtualEnvUtil.buildWithProjectGroup(consumerGroup, projectGroupPrefix);
            msg.setTopic(VirtualEnvUtil.buildWithProjectGroup(msg.getTopic(), projectGroupPrefix));
        }

        ConsumerSendMsgBackRequestHeader requestHeader = new ConsumerSendMsgBackRequestHeader();
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.CONSUMER_SEND_MSG_BACK, requestHeader);

        requestHeader.setGroup(consumerGroupWithProjectGroup);
        requestHeader.setOriginTopic(msg.getTopic());
        requestHeader.setOffset(msg.getCommitLogOffset());
        requestHeader.setDelayLevel(delayLevel);
        requestHeader.setOriginMsgId(msg.getMsgId());

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    public Set<MessageQueue> lockBatchMQ(//
            final String addr,//
            final LockBatchRequestBody requestBody,//
            final long timeoutMillis) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            requestBody.setConsumerGroup((VirtualEnvUtil.buildWithProjectGroup(
                requestBody.getConsumerGroup(), projectGroupPrefix)));
            Set<MessageQueue> messageQueues = requestBody.getMqSet();
            for (MessageQueue messageQueue : messageQueues) {
                messageQueue.setTopic(VirtualEnvUtil.buildWithProjectGroup(messageQueue.getTopic(),
                    projectGroupPrefix));
            }
        }

        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.LOCK_BATCH_MQ, null);

        request.setBody(requestBody.encode());
        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            LockBatchResponseBody responseBody =
                    LockBatchResponseBody.decode(response.getBody(), LockBatchResponseBody.class);
            Set<MessageQueue> messageQueues = responseBody.getLockOKMQSet();
            // 清除虚拟运行环境相关的projectGroupPrefix
            if (!UtilAll.isBlank(projectGroupPrefix)) {
                for (MessageQueue messageQueue : messageQueues) {
                    messageQueue.setTopic(VirtualEnvUtil.clearProjectGroup(messageQueue.getTopic(),
                        projectGroupPrefix));
                }
            }
            return messageQueues;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    public void unlockBatchMQ(//
            final String addr,//
            final UnlockBatchRequestBody requestBody,//
            final long timeoutMillis,//
            final boolean oneway//
    ) throws RemotingException, MQBrokerException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            requestBody.setConsumerGroup(VirtualEnvUtil.buildWithProjectGroup(requestBody.getConsumerGroup(),
                projectGroupPrefix));
            Set<MessageQueue> messageQueues = requestBody.getMqSet();
            for (MessageQueue messageQueue : messageQueues) {
                messageQueue.setTopic(VirtualEnvUtil.buildWithProjectGroup(messageQueue.getTopic(),
                    projectGroupPrefix));
            }
        }

        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.UNLOCK_BATCH_MQ, null);

        request.setBody(requestBody.encode());

        if (oneway) {
            this.remotingClient.invokeOneway(addr, request, timeoutMillis);
        }
        else {
            RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
            switch (response.getCode()) {
            case ResponseCode.SUCCESS: {
                return;
            }
            default:
                break;
            }

            throw new MQBrokerException(response.getCode(), response.getRemark());
        }
    }


    public TopicStatsTable getTopicStatsInfo(final String addr, final String topic, final long timeoutMillis)
            throws InterruptedException, RemotingTimeoutException, RemotingSendRequestException,
            RemotingConnectException, MQBrokerException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String topicWithProjectGroup = topic;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            topicWithProjectGroup = VirtualEnvUtil.buildWithProjectGroup(topic, projectGroupPrefix);
        }

        GetTopicStatsInfoRequestHeader requestHeader = new GetTopicStatsInfoRequestHeader();
        requestHeader.setTopic(topicWithProjectGroup);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_TOPIC_STATS_INFO, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            TopicStatsTable topicStatsTable =
                    TopicStatsTable.decode(response.getBody(), TopicStatsTable.class);
            // 清除虚拟运行环境相关的projectGroupPrefix
            if (!UtilAll.isBlank(projectGroupPrefix)) {
                HashMap<MessageQueue, TopicOffset> newTopicOffsetMap =
                        new HashMap<MessageQueue, TopicOffset>();
                for (Map.Entry<MessageQueue, TopicOffset> messageQueue : topicStatsTable.getOffsetTable()
                    .entrySet()) {
                    MessageQueue key = messageQueue.getKey();
                    key.setTopic(VirtualEnvUtil.clearProjectGroup(key.getTopic(), projectGroupPrefix));
                    newTopicOffsetMap.put(key, messageQueue.getValue());
                }
                topicStatsTable.setOffsetTable(newTopicOffsetMap);
            }
            return topicStatsTable;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    public ConsumeStats getConsumeStats(final String addr, final String consumerGroup,
            final long timeoutMillis) throws InterruptedException, RemotingTimeoutException,
            RemotingSendRequestException, RemotingConnectException, MQBrokerException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String consumerGroupWithProjectGroup = consumerGroup;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            consumerGroupWithProjectGroup =
                    VirtualEnvUtil.buildWithProjectGroup(consumerGroup, projectGroupPrefix);
        }

        GetConsumeStatsRequestHeader requestHeader = new GetConsumeStatsRequestHeader();
        requestHeader.setConsumerGroup(consumerGroupWithProjectGroup);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_CONSUME_STATS, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            ConsumeStats consumeStats = ConsumeStats.decode(response.getBody(), ConsumeStats.class);
            // 清除虚拟运行环境相关的projectGroupPrefix
            if (!UtilAll.isBlank(projectGroupPrefix)) {
                HashMap<MessageQueue, OffsetWrapper> newTopicOffsetMap =
                        new HashMap<MessageQueue, OffsetWrapper>();
                for (Map.Entry<MessageQueue, OffsetWrapper> messageQueue : consumeStats.getOffsetTable()
                    .entrySet()) {
                    MessageQueue key = messageQueue.getKey();
                    key.setTopic(VirtualEnvUtil.clearProjectGroup(key.getTopic(), projectGroupPrefix));
                    newTopicOffsetMap.put(key, messageQueue.getValue());
                }
                consumeStats.setOffsetTable(newTopicOffsetMap);
            }

            return consumeStats;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 根据ProducerGroup获取Producer连接列表
     */
    public ProducerConnection getProducerConnectionList(final String addr, final String producerGroup,
            final long timeoutMillis) throws RemotingConnectException, RemotingSendRequestException,
            RemotingTimeoutException, InterruptedException, MQBrokerException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String producerGroupWithProjectGroup = producerGroup;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            producerGroupWithProjectGroup =
                    VirtualEnvUtil.buildWithProjectGroup(producerGroup, projectGroupPrefix);
        }

        GetProducerConnectionListRequestHeader requestHeader = new GetProducerConnectionListRequestHeader();
        requestHeader.setProducerGroup(producerGroupWithProjectGroup);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_PRODUCER_CONNECTION_LIST, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return ProducerConnection.decode(response.getBody(), ProducerConnection.class);
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 根据ConsumerGroup获取Consumer连接列表以及订阅关系
     */
    public ConsumerConnection getConsumerConnectionList(final String addr, final String consumerGroup,
            final long timeoutMillis) throws RemotingConnectException, RemotingSendRequestException,
            RemotingTimeoutException, InterruptedException, MQBrokerException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String consumerGroupWithProjectGroup = consumerGroup;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            consumerGroupWithProjectGroup =
                    VirtualEnvUtil.buildWithProjectGroup(consumerGroup, projectGroupPrefix);
        }

        GetConsumerConnectionListRequestHeader requestHeader = new GetConsumerConnectionListRequestHeader();
        requestHeader.setConsumerGroup(consumerGroupWithProjectGroup);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_CONSUMER_CONNECTION_LIST, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            ConsumerConnection consumerConnection =
                    ConsumerConnection.decode(response.getBody(), ConsumerConnection.class);
            if (!UtilAll.isBlank(projectGroupPrefix)) {
                ConcurrentHashMap<String, SubscriptionData> subscriptionDataConcurrentHashMap =
                        consumerConnection.getSubscriptionTable();
                for (Map.Entry<String, SubscriptionData> subscriptionDataEntry : subscriptionDataConcurrentHashMap
                    .entrySet()) {
                    SubscriptionData subscriptionData = subscriptionDataEntry.getValue();
                    subscriptionDataEntry.getValue().setTopic(
                        VirtualEnvUtil.clearProjectGroup(subscriptionData.getTopic(), projectGroupPrefix));
                }
            }
            return consumerConnection;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    public KVTable getBrokerRuntimeInfo(final String addr, final long timeoutMillis)
            throws RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException,
            InterruptedException, MQBrokerException {

        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.GET_BROKER_RUNTIME_INFO, null);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return KVTable.decode(response.getBody(), KVTable.class);
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 更新Broker的配置文件
     * 
     * @param addr
     * @param properties
     * @param timeoutMillis
     * @throws RemotingConnectException
     * @throws RemotingSendRequestException
     * @throws RemotingTimeoutException
     * @throws InterruptedException
     * @throws MQBrokerException
     * @throws UnsupportedEncodingException
     */
    public void updateBrokerConfig(final String addr, final Properties properties, final long timeoutMillis)
            throws RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException,
            InterruptedException, MQBrokerException, UnsupportedEncodingException {

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.UPDATE_BROKER_CONFIG, null);

        String str = MixAll.properties2String(properties);
        if (str != null && str.length() > 0) {
            request.setBody(str.getBytes(MixAll.DEFAULT_CHARSET));
            RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
            switch (response.getCode()) {
            case ResponseCode.SUCCESS: {
                return;
            }
            default:
                break;
            }

            throw new MQBrokerException(response.getCode(), response.getRemark());
        }
    }


    /**
     * Name Server: 从Name Server获取集群信息
     */
    public ClusterInfo getBrokerClusterInfo(final long timeoutMillis) throws InterruptedException,
            RemotingTimeoutException, RemotingSendRequestException, RemotingConnectException,
            MQBrokerException {
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_BROKER_CLUSTER_INFO, null);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            ClusterInfo responseBody = ClusterInfo.decode(response.getBody(), ClusterInfo.class);
            return responseBody;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());

    }


    /**
     * Name Server: 从Name Server获取 Default Topic 路由信息
     */
    public TopicRouteData getDefaultTopicRouteInfoFromNameServer(final String topic, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        GetRouteInfoRequestHeader requestHeader = new GetRouteInfoRequestHeader();
        requestHeader.setTopic(topic);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_ROUTE_INFO_BY_TOPIC, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.TOPIC_NOT_EXIST: {
            // TODO LOG
            break;
        }
        case ResponseCode.SUCCESS: {
            byte[] body = response.getBody();
            if (body != null) {
                return TopicRouteData.decode(body, TopicRouteData.class);
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }

    public TopicRouteData getTopicRouteInfoFromNameServer(final String topic, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        return getTopicRouteInfoFromNameServer(topic, true, timeoutMillis);
    }

    /**
     * Name Server: 从Name Server获取Topic路由信息
     */
    public TopicRouteData getTopicRouteInfoFromNameServer(final String topic, boolean logWarning, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String topicWithProjectGroup = topic;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            topicWithProjectGroup = VirtualEnvUtil.buildWithProjectGroup(topic, projectGroupPrefix);
        }

        GetRouteInfoRequestHeader requestHeader = new GetRouteInfoRequestHeader();
        requestHeader.setTopic(topicWithProjectGroup);

        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.GET_ROUTE_INFO_BY_TOPIC, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
            case ResponseCode.TOPIC_NOT_EXIST: {
                if (logWarning) {
                    log.warn("getRouteInfoFromNameServer for topic [{}] failed. Response Code: TOPIC_NOT_EXIST; Remark: {}",
                            topic, response.getRemark());
                }
                break;
            }

            case ResponseCode.SUCCESS: {
                byte[] body = response.getBody();
                if (body != null) {
                    return TopicRouteData.decode(body, TopicRouteData.class);
                }
            }

            default:
                break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * Name Server: 从Name Server获取所有Topic列表
     */
    public TopicList getTopicListFromNameServer(final long timeoutMillis) throws RemotingException,
            MQClientException, InterruptedException {
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_ALL_TOPIC_LIST_FROM_NAMESERVER, null);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            byte[] body = response.getBody();
            if (body != null) {
                TopicList topicList = TopicList.decode(body, TopicList.class);

                if (!UtilAll.isBlank(projectGroupPrefix)) {
                    HashSet<String> newTopicSet = new HashSet<String>();
                    for (String topic : topicList.getTopicList()) {
                        newTopicSet.add(VirtualEnvUtil.clearProjectGroup(topic, projectGroupPrefix));
                    }
                    topicList.setTopicList(newTopicSet);
                }
                return topicList;
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * Name Server: Broker下线前，清除Broker对应的权限
     */
    public int wipeWritePermOfBroker(final String namesrvAddr, String brokerName, final long timeoutMillis)
            throws RemotingCommandException, RemotingConnectException, RemotingSendRequestException,
            RemotingTimeoutException, InterruptedException, MQClientException {
        WipeWritePermOfBrokerRequestHeader requestHeader = new WipeWritePermOfBrokerRequestHeader();
        requestHeader.setBrokerName(brokerName);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.WIPE_WRITE_PERM_OF_BROKER, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(namesrvAddr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            WipeWritePermOfBrokerResponseHeader responseHeader =
                    (WipeWritePermOfBrokerResponseHeader) response
                        .decodeCommandCustomHeader(WipeWritePermOfBrokerResponseHeader.class);
            return responseHeader.getWipeTopicCount();
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    public void deleteTopicInBroker(final String addr, final String topic, final long timeoutMillis)
            throws RemotingException, MQBrokerException, InterruptedException, MQClientException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String topicWithProjectGroup = topic;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            topicWithProjectGroup = VirtualEnvUtil.buildWithProjectGroup(topic, projectGroupPrefix);
        }

        DeleteTopicRequestHeader requestHeader = new DeleteTopicRequestHeader();
        requestHeader.setTopic(topicWithProjectGroup);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.DELETE_TOPIC_IN_BROKER, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    public void deleteTopicInNameServer(final String nameServerAddress,
                                        final String topic,
                                        String brokerAddresses,
                                        final long timeoutMillis)
            throws RemotingException, MQBrokerException, InterruptedException, MQClientException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String topicWithProjectGroup = topic;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            topicWithProjectGroup = VirtualEnvUtil.buildWithProjectGroup(topic, projectGroupPrefix);
        }

        DeleteTopicInNamesrvRequestHeader requestHeader = new DeleteTopicInNamesrvRequestHeader();
        requestHeader.setTopic(topicWithProjectGroup);

        if (null != brokerAddresses) {
            requestHeader.setBrokerAddresses(brokerAddresses);
            log.info("Topic is deleted from name server: {} for brokers: {} ", nameServerAddress, brokerAddresses);
        } else {
            log.info("Topic is deleted cluster wide from name server: {}", nameServerAddress);
        }

        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.DELETE_TOPIC_IN_NAMESRV, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(nameServerAddress, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    public void deleteSubscriptionGroup(final String addr, final String groupName, final long timeoutMillis)
            throws RemotingException, MQBrokerException, InterruptedException, MQClientException {
        // 添加虚拟运行环境相关的projectGroupPrefix
        String groupWithProjectGroup = groupName;
        if (!UtilAll.isBlank(projectGroupPrefix)) {
            groupWithProjectGroup = VirtualEnvUtil.buildWithProjectGroup(groupName, projectGroupPrefix);
        }

        DeleteSubscriptionGroupRequestHeader requestHeader = new DeleteSubscriptionGroupRequestHeader();
        requestHeader.setGroupName(groupWithProjectGroup);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.DELETE_SUBSCRIPTION_GROUP, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * Name Server: 从Namesrv获取KV配置
     */
    public String getKVConfigValue(final String namespace, final String key, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        GetKVConfigRequestHeader requestHeader = new GetKVConfigRequestHeader();
        requestHeader.setNamespace(namespace);
        requestHeader.setKey(key);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_KV_CONFIG, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            GetKVConfigResponseHeader responseHeader =
                    (GetKVConfigResponseHeader) response
                        .decodeCommandCustomHeader(GetKVConfigResponseHeader.class);
            return responseHeader.getValue();
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * Name Server: 添加KV配置
     */
    public void putKVConfigValue(final String namespace, final String key, final String value,
            final long timeoutMillis) throws RemotingException, MQClientException, InterruptedException {
        PutKVConfigRequestHeader requestHeader = new PutKVConfigRequestHeader();
        requestHeader.setNamespace(namespace);
        requestHeader.setKey(key);
        requestHeader.setValue(value);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.PUT_KV_CONFIG, requestHeader);

        List<String> nameServerAddressList = this.remotingClient.getNameServerAddressList();
        if (nameServerAddressList != null) {
            RemotingCommand errResponse = null;
            for (String namesrvAddr : nameServerAddressList) {
                RemotingCommand response =
                        this.remotingClient.invokeSync(namesrvAddr, request, timeoutMillis);
                assert response != null;
                switch (response.getCode()) {
                case ResponseCode.SUCCESS: {
                    break;
                }
                default:
                    errResponse = response;
                }
            }

            if (errResponse != null) {
                throw new MQClientException(errResponse.getCode(), errResponse.getRemark());
            }
        }
    }


    /**
     * Name Server: 添加KV配置
     */
    public void deleteKVConfigValue(final String namespace, final String key, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        DeleteKVConfigRequestHeader requestHeader = new DeleteKVConfigRequestHeader();
        requestHeader.setNamespace(namespace);
        requestHeader.setKey(key);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.DELETE_KV_CONFIG, requestHeader);

        List<String> nameServerAddressList = this.remotingClient.getNameServerAddressList();
        if (nameServerAddressList != null) {
            RemotingCommand errResponse = null;
            for (String namesrvAddr : nameServerAddressList) {
                RemotingCommand response =
                        this.remotingClient.invokeSync(namesrvAddr, request, timeoutMillis);
                assert response != null;
                switch (response.getCode()) {
                case ResponseCode.SUCCESS: {
                    break;
                }
                default:
                    errResponse = response;
                }
            }
            if (errResponse != null) {
                throw new MQClientException(errResponse.getCode(), errResponse.getRemark());
            }
        }
    }


    /**
     * Name Server: 通过 server ip 获取 project 信息
     */
    public String getProjectGroupByIp(String ip, final long timeoutMillis) throws RemotingException,
            MQClientException, InterruptedException {
        return getKVConfigValue(NamesrvUtil.NAMESPACE_PROJECT_CONFIG, ip, timeoutMillis);
    }


    /**
     * Name Server: 通过 value 获取所有的 key 信息
     */
    public String getKVConfigByValue(final String namespace, String value, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        GetKVConfigRequestHeader requestHeader = new GetKVConfigRequestHeader();
        requestHeader.setNamespace(namespace);
        requestHeader.setKey(value);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_KV_CONFIG_BY_VALUE, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            GetKVConfigResponseHeader responseHeader =
                    (GetKVConfigResponseHeader) response
                        .decodeCommandCustomHeader(GetKVConfigResponseHeader.class);
            return responseHeader.getValue();
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * Name Server: 获取指定Namespace下的所有KV
     */
    public KVTable getKVListByNamespace(final String namespace, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        GetKVListByNamespaceRequestHeader requestHeader = new GetKVListByNamespaceRequestHeader();
        requestHeader.setNamespace(namespace);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_KVLIST_BY_NAMESPACE, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return KVTable.decode(response.getBody(), KVTable.class);
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * Name Server: 删除 value 对应的所有 key
     */
    public void deleteKVConfigByValue(final String namespace, final String projectGroup,
            final long timeoutMillis) throws RemotingException, MQClientException, InterruptedException {
        DeleteKVConfigRequestHeader requestHeader = new DeleteKVConfigRequestHeader();
        requestHeader.setNamespace(namespace);
        requestHeader.setKey(projectGroup);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.DELETE_KV_CONFIG_BY_VALUE, requestHeader);

        List<String> nameServerAddressList = this.remotingClient.getNameServerAddressList();

        if (nameServerAddressList != null) {
            RemotingCommand errResponse = null;
            for (String namesrvAddr : nameServerAddressList) {
                RemotingCommand response =
                        this.remotingClient.invokeSync(namesrvAddr, request, timeoutMillis);
                assert response != null;
                switch (response.getCode()) {
                case ResponseCode.SUCCESS: {
                    break;
                }
                default:
                    errResponse = response;
                }
            }
            if (errResponse != null) {
                throw new MQClientException(errResponse.getCode(), errResponse.getRemark());
            }
        }
    }


    /**
     * 通知 broker 重置 offset
     */
    public Map<MessageQueue, Long> invokeBrokerToResetOffset(final String addr, final String topic,
            final String group, final long timestamp, final boolean isForce, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        ResetOffsetRequestHeader requestHeader = new ResetOffsetRequestHeader();
        requestHeader.setTopic(topic);
        requestHeader.setGroup(group);
        requestHeader.setTimestamp(timestamp);
        requestHeader.setForce(isForce);

        RemotingCommand request =
                RemotingCommand
                    .createRequestCommand(RequestCode.INVOKE_BROKER_TO_RESET_OFFSET, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            if (response.getBody() != null) {
                ResetOffsetBody body = ResetOffsetBody.decode(response.getBody(), ResetOffsetBody.class);
                return body.getOffsetTable();
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * 通知 broker 客户端订阅消息处理
     */
    public Map<String, Map<MessageQueue, Long>> invokeBrokerToGetConsumerStatus(final String addr,
            final String topic, final String group, final String clientAddr, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        GetConsumerStatusRequestHeader requestHeader = new GetConsumerStatusRequestHeader();
        requestHeader.setTopic(topic);
        requestHeader.setGroup(group);
        requestHeader.setClientAddr(clientAddr);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.INVOKE_BROKER_TO_GET_CONSUMER_STATUS,
                    requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            if (response.getBody() != null) {
                GetConsumerStatusBody body =
                        GetConsumerStatusBody.decode(response.getBody(), GetConsumerStatusBody.class);
                return body.getConsumerTable();
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * 根据ConsumerGroup获取Consumer连接列表以及订阅关系
     */
    public GroupList queryTopicConsumeByWho(final String addr, final String topic, final long timeoutMillis)
            throws RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException,
            InterruptedException, MQBrokerException {
        QueryTopicConsumeByWhoRequestHeader requestHeader = new QueryTopicConsumeByWhoRequestHeader();
        requestHeader.setTopic(topic);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.QUERY_TOPIC_CONSUME_BY_WHO, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            GroupList groupList = GroupList.decode(response.getBody(), GroupList.class);
            return groupList;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 根据 topic 和 group 获取消息的时间跨度
     */
    public Set<QueueTimeSpan> queryConsumeTimeSpan(final String addr, final String topic, final String group,
            final long timeoutMillis) throws RemotingConnectException, RemotingSendRequestException,
            RemotingTimeoutException, InterruptedException, MQBrokerException {
        QueryConsumeTimeSpanRequestHeader requestHeader = new QueryConsumeTimeSpanRequestHeader();
        requestHeader.setTopic(topic);
        requestHeader.setGroup(group);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.QUERY_CONSUME_TIME_SPAN, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            QueryConsumeTimeSpanBody consumeTimeSpanBody =
                    GroupList.decode(response.getBody(), QueryConsumeTimeSpanBody.class);
            return consumeTimeSpanBody.getConsumeTimeSpanSet();
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * Name Server: 获取指定集群下的所有 topic
     */
    public TopicList getTopicsByCluster(final String cluster, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        GetTopicsByClusterRequestHeader requestHeader = new GetTopicsByClusterRequestHeader();
        requestHeader.setCluster(cluster);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_TOPICS_BY_CLUSTER, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            byte[] body = response.getBody();
            if (body != null) {
                TopicList topicList = TopicList.decode(body, TopicList.class);

                if (!UtilAll.isBlank(projectGroupPrefix)) {
                    HashSet<String> newTopicSet = new HashSet<String>();
                    for (String topic : topicList.getTopicList()) {
                        newTopicSet.add(VirtualEnvUtil.clearProjectGroup(topic, projectGroupPrefix));
                    }
                    topicList.setTopicList(newTopicSet);
                }
                return topicList;
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }

    public SubscriptionGroupWrapper getSubscriptionGroupsFromBroker(final String brokerAddress, long timeout) throws InterruptedException,
            RemotingTimeoutException, RemotingSendRequestException, RemotingConnectException, MQClientException {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.GET_ALL_SUBSCRIPTION_GROUP_CONFIG, null);
        RemotingCommand response = remotingClient.invokeSync(brokerAddress, request, timeout);
        switch (response.getCode()) {
            case ResponseCode.SUCCESS:
                return SubscriptionGroupWrapper.decode(response.getBody(), SubscriptionGroupWrapper.class);
            default:
                break;
        }
        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * Filter Server: 向Filter Server注册过滤类
     * 
     * @throws InterruptedException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     * @throws RemotingConnectException
     * @throws MQBrokerException
     */
    public void registerMessageFilterClass(final String addr,//
            final String consumerGroup,//
            final String topic,//
            final String className,//
            final int classCRC,//
            final byte[] classBody,//
            final long timeoutMillis) throws RemotingConnectException, RemotingSendRequestException,
            RemotingTimeoutException, InterruptedException, MQBrokerException {
        RegisterMessageFilterClassRequestHeader requestHeader = new RegisterMessageFilterClassRequestHeader();
        requestHeader.setConsumerGroup(consumerGroup);
        requestHeader.setClassName(className);
        requestHeader.setTopic(topic);
        requestHeader.setClassCRC(classCRC);

        RemotingCommand request =
                RemotingCommand
                    .createRequestCommand(RequestCode.REGISTER_MESSAGE_FILTER_CLASS, requestHeader);
        request.setBody(classBody);
        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }


    /**
     * 获取所有系统内置 Topic 列表
     */
    public TopicList getSystemTopicList(final long timeoutMillis) throws RemotingException,
            MQClientException, InterruptedException {
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_SYSTEM_TOPIC_LIST_FROM_NS, null);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            byte[] body = response.getBody();
            if (body != null) {
                TopicList topicList = TopicList.decode(response.getBody(), TopicList.class);
                if (!UtilAll.isBlank(projectGroupPrefix)) {
                    HashSet<String> newTopicSet = new HashSet<String>();
                    for (String topic : topicList.getTopicList()) {
                        newTopicSet.add(VirtualEnvUtil.clearProjectGroup(topic, projectGroupPrefix));
                    }
                    topicList.setTopicList(newTopicSet);
                }

                if (topicList.getTopicList() != null && !topicList.getTopicList().isEmpty()
                        && !UtilAll.isBlank(topicList.getBrokerAddr())) {
                    TopicList tmp = getSystemTopicListFromBroker(topicList.getBrokerAddr(), timeoutMillis);
                    if (tmp.getTopicList() != null && !tmp.getTopicList().isEmpty()) {
                        topicList.getTopicList().addAll(tmp.getTopicList());
                    }
                }
                return topicList;
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * Name Server: 从Name Server获取所有系统内置 Topic 列表
     */
    public TopicList getSystemTopicListFromBroker(final String addr, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_SYSTEM_TOPIC_LIST_FROM_BROKER, null);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            byte[] body = response.getBody();
            if (body != null) {
                TopicList topicList = TopicList.decode(body, TopicList.class);
                if (!UtilAll.isBlank(projectGroupPrefix)) {
                    HashSet<String> newTopicSet = new HashSet<String>();
                    for (String topic : topicList.getTopicList()) {
                        newTopicSet.add(VirtualEnvUtil.clearProjectGroup(topic, projectGroupPrefix));
                    }
                    topicList.setTopicList(newTopicSet);
                }
                return topicList;
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    public boolean cleanExpiredConsumeQueue(final String addr, long timeoutMillis) throws MQClientException,
            RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException,
            InterruptedException {
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.CLEAN_EXPIRED_CONSUME_QUEUE, null);
        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return true;
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * 通过调用Broker，从Consumer内存获取相应数据结构
     */
    public ConsumerRunningInfo getConsumerRunningInfo(final String addr, String consumerGroup,
            String clientId, boolean jstack, final long timeoutMillis) throws RemotingException,
            MQClientException, InterruptedException {
        GetConsumerRunningInfoRequestHeader requestHeader = new GetConsumerRunningInfoRequestHeader();
        requestHeader.setConsumerGroup(consumerGroup);
        requestHeader.setClientId(clientId);
        requestHeader.setJstackEnable(jstack);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_CONSUMER_RUNNING_INFO, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            byte[] body = response.getBody();
            if (body != null) {
                ConsumerRunningInfo info = ConsumerRunningInfo.decode(body, ConsumerRunningInfo.class);
                return info;
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * 通过调用Broker，向指定Consumer发送某条消息，并返回消费结果
     */
    public ConsumeMessageDirectlyResult consumeMessageDirectly(final String addr, //
            String consumerGroup, //
            String clientId, //
            String msgId, //
            final long timeoutMillis) throws RemotingException, MQClientException, InterruptedException {
        ConsumeMessageDirectlyResultRequestHeader requestHeader =
                new ConsumeMessageDirectlyResultRequestHeader();
        requestHeader.setConsumerGroup(consumerGroup);
        requestHeader.setClientId(clientId);
        requestHeader.setMsgId(msgId);

        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.CONSUME_MESSAGE_DIRECTLY, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            byte[] body = response.getBody();
            if (body != null) {
                ConsumeMessageDirectlyResult info =
                        ConsumeMessageDirectlyResult.decode(body, ConsumeMessageDirectlyResult.class);
                return info;
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    public String getProjectGroupPrefix() {
        return projectGroupPrefix;
    }


    public Map<Integer, Long> queryCorrectionOffset(final String addr, final String topic,
            final String group, Set<String> filterGroup, long timeoutMillis) throws MQClientException,
            RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException,
            InterruptedException {
        QueryCorrectionOffsetHeader requestHeader = new QueryCorrectionOffsetHeader();
        requestHeader.setCompareGroup(group);
        requestHeader.setTopic(topic);
        if (filterGroup != null) {
            StringBuilder sb = new StringBuilder();
            String splitor = "";
            for (String s : filterGroup) {
                sb.append(splitor).append(s);
                splitor = ",";
            }
            requestHeader.setFilterGroups(sb.toString());
        }
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.QUERY_CORRECTION_OFFSET, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            if (response.getBody() != null) {
                QueryCorrectionOffsetBody body =
                        QueryCorrectionOffsetBody.decode(response.getBody(), QueryCorrectionOffsetBody.class);
                return body.getCorrectionOffsets();
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * 获取单元化逻辑 Topic 列表
     */
    public TopicList getUnitTopicList(final boolean containRetry, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.GET_UNIT_TOPIC_LIST, null);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            byte[] body = response.getBody();
            if (body != null) {
                TopicList topicList = TopicList.decode(response.getBody(), TopicList.class);
                if (!UtilAll.isBlank(projectGroupPrefix)) {
                    HashSet<String> newTopicSet = new HashSet<String>();
                    for (String topic : topicList.getTopicList()) {
                        newTopicSet.add(VirtualEnvUtil.clearProjectGroup(topic, projectGroupPrefix));
                    }
                    topicList.setTopicList(newTopicSet);
                }
                if (!containRetry) {
                    Iterator<String> it = topicList.getTopicList().iterator();
                    while (it.hasNext()) {
                        String topic = it.next();
                        if (topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX))
                            it.remove();
                    }
                }

                return topicList;
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * 获取含有单元化订阅组的 Topic 列表
     */
    public TopicList getHasUnitSubTopicList(final boolean containRetry, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_HAS_UNIT_SUB_TOPIC_LIST, null);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            byte[] body = response.getBody();
            if (body != null) {
                TopicList topicList = TopicList.decode(response.getBody(), TopicList.class);
                if (!UtilAll.isBlank(projectGroupPrefix)) {
                    HashSet<String> newTopicSet = new HashSet<String>();
                    for (String topic : topicList.getTopicList()) {
                        newTopicSet.add(VirtualEnvUtil.clearProjectGroup(topic, projectGroupPrefix));
                    }
                    topicList.setTopicList(newTopicSet);
                }
                if (!containRetry) {
                    Iterator<String> it = topicList.getTopicList().iterator();
                    while (it.hasNext()) {
                        String topic = it.next();
                        if (topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX))
                            it.remove();
                    }
                }
                return topicList;
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * 获取含有单元化订阅组的非单元化 Topic 列表
     */
    public TopicList getHasUnitSubUnUnitTopicList(final boolean containRetry, final long timeoutMillis)
            throws RemotingException, MQClientException, InterruptedException {
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_HAS_UNIT_SUB_UNUNIT_TOPIC_LIST, null);

        RemotingCommand response = this.remotingClient.invokeSync(null, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            byte[] body = response.getBody();
            if (body != null) {
                TopicList topicList = TopicList.decode(response.getBody(), TopicList.class);
                if (!UtilAll.isBlank(projectGroupPrefix)) {
                    HashSet<String> newTopicSet = new HashSet<String>();
                    for (String topic : topicList.getTopicList()) {
                        newTopicSet.add(VirtualEnvUtil.clearProjectGroup(topic, projectGroupPrefix));
                    }
                    topicList.setTopicList(newTopicSet);
                }
                if (!containRetry) {
                    Iterator<String> it = topicList.getTopicList().iterator();
                    while (it.hasNext()) {
                        String topic = it.next();
                        if (topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX))
                            it.remove();
                    }
                }
                return topicList;
            }
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }


    /**
     * 克隆某一个组的消费进度到新的组
     */
    public void cloneGroupOffset(final String addr, final String srcGroup, final String destGroup,
            final String topic, final boolean isOffline, final long timeoutMillis) throws RemotingException,
            MQClientException, InterruptedException {
        CloneGroupOffsetRequestHeader requestHeader = new CloneGroupOffsetRequestHeader();
        requestHeader.setSrcGroup(srcGroup);
        requestHeader.setDestGroup(destGroup);
        requestHeader.setTopic(topic);
        requestHeader.setOffline(isOffline);
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.CLONE_GROUP_OFFSET, null);

        RemotingCommand response = this.remotingClient.invokeSync(addr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
        case ResponseCode.SUCCESS: {
            return;
        }
        default:
            break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }

    public BrokerStatsData viewBrokerStatsData(String brokerAddr, String statsName, String statsKey,
                                               long timeoutMillis) throws MQClientException, RemotingConnectException,
            RemotingSendRequestException, RemotingTimeoutException, InterruptedException {
        ViewBrokerStatsDataRequestHeader requestHeader = new ViewBrokerStatsDataRequestHeader();
        requestHeader.setStatsName(statsName);
        requestHeader.setStatsKey(statsKey);

        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.VIEW_BROKER_STATS_DATA, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(brokerAddr, request, timeoutMillis);
        assert response != null;
        switch (response.getCode()) {
            case ResponseCode.SUCCESS: {
                byte[] body = response.getBody();
                if (body != null) {
                    return BrokerStatsData.decode(body, BrokerStatsData.class);
                }
            }
            default:
                break;
        }

        throw new MQClientException(response.getCode(), response.getRemark());
    }

    public void addCommitLogStorePath(String brokerAddress, AddCommitLogStorePathRequestHeader requestHeader)
            throws InterruptedException, RemotingTimeoutException, RemotingSendRequestException,
            RemotingConnectException, MQBrokerException {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.ADD_COMMIT_LOG_STORE_PATH, requestHeader);
        RemotingCommand response = this.remotingClient.invokeSync(brokerAddress, request, 3000);
        assert response != null;
        switch (response.getCode()) {
            case ResponseCode.SUCCESS:
                return;
            default:
                break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }
}
