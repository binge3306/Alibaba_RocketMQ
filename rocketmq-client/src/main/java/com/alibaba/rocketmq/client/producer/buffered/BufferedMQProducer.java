package com.alibaba.rocketmq.client.producer.buffered;

import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.client.log.ClientLogger;
import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.MessageQueueSelector;
import com.alibaba.rocketmq.client.producer.SendCallback;
import com.alibaba.rocketmq.client.producer.TraceLevel;
import com.alibaba.rocketmq.client.producer.selector.Region;
import com.alibaba.rocketmq.client.producer.selector.SelectMessageQueueByRegion;
import com.alibaba.rocketmq.client.store.DefaultLocalMessageStore;
import com.alibaba.rocketmq.client.store.LocalMessageStore;
import com.alibaba.rocketmq.common.ThreadFactoryImpl;
import com.alibaba.rocketmq.common.message.Message;
import com.alibaba.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Deprecated
public class BufferedMQProducer {

    private static final Logger LOGGER = ClientLogger.getLog();

    private static final int MAX_NUMBER_OF_MESSAGE_IN_QUEUE = 50000;

    private final ScheduledExecutorService executorService;

    private final DefaultMQProducer producer;

    private SendCallback sendCallback;

    private LocalMessageStore localMessageStore;

    private final AtomicLong successSendingCounter;
    private long success;
    private final AtomicLong errorSendingCounter;
    private long error;

    private MessageQueueSelector messageQueueSelector;

    private final LinkedBlockingQueue<Message> messageQueue;

    private final MessageSender messageSender;

    private Region targetRegion = Region.SAME;

    public BufferedMQProducer(String producerGroup) throws IOException {
        executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryImpl("ResendFailureMessageService"));
        successSendingCounter = new AtomicLong();
        errorSendingCounter = new AtomicLong();
        producer = new DefaultMQProducer(producerGroup);
        producer.setTraceLevel(TraceLevel.PRODUCTION.name());
        localMessageStore = new DefaultLocalMessageStore(producerGroup);
        messageQueue = new LinkedBlockingQueue<>(MAX_NUMBER_OF_MESSAGE_IN_QUEUE);
        addShutdownHook();
        messageSender = new MessageSender();
    }

    public void start() throws MQClientException, IOException {
        messageQueueSelector = new SelectMessageQueueByRegion(targetRegion);
        localMessageStore.start();
        producer.start();
        scheduleResendMessageService();
        scheduleTPSReport();
        Thread messageSendingThread = new Thread(messageSender);
        messageSendingThread.setName("MessageSendingService");
        messageSendingThread.start();
        LOGGER.info("Producer starts");
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                LOGGER.warn("Multi-thread MQ Producer shutdown hook invoked.");
                try {
                    shutdown();
                } catch (InterruptedException e) {
                    LOGGER.error("ShutdownHook error.", e);
                }
                LOGGER.warn("Multi-thread MQ Producer shutdown completes.");
            }
        });
    }

    private void scheduleTPSReport() {
        executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                long s = successSendingCounter.get();
                long e = errorSendingCounter.get();
                long successDiff = s - success;
                long errorDiff = e - error;
                LOGGER.info("Success TPS: {}, Error TPS: {}", successDiff / 30, errorDiff / 30);
                success = s;
                error = e;
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void scheduleResendMessageService() {
        LOGGER.info("Schedule send failure message");
        executorService.scheduleWithFixedDelay(new ResendMessageTask(localMessageStore, this), 30, 30, TimeUnit.SECONDS);
    }

    public void registerCallback(SendCallback sendCallback) {
        this.sendCallback = sendCallback;
    }

    public void setSendMsgTimeout(int timeout) {
        producer.setSendMsgTimeout(timeout);
    }

    public void send(final Message msg) {
        try {
            producer.send(msg, messageQueueSelector, null, new SendMessageCallback(this, sendCallback, msg));
        } catch (MQClientException | RemotingException | InterruptedException e) {
            errorSendingCounter.incrementAndGet();
            try {
                messageQueue.offer(msg, 5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                localMessageStore.stash(msg);
            }
        }
    }

    public void send(final Message[] messages) {
        for (Message message : messages) {
            send(message);
        }
    }

    /**
     * This method properly shutdown this producer client.
     *
     * @throws InterruptedException if unable to shutdown within 1 minute.
     */
    public void shutdown() throws InterruptedException {
        LOGGER.warn("BufferedMQProducer starts to shutdown.");

        //Stop thread which pops messages from local message store.
        executorService.shutdown();
        executorService.awaitTermination(30000, TimeUnit.MILLISECONDS);

        messageSender.stop();

        producer.shutdown();

        Message message = null;
        while (messageQueue.size() > 0) {
            message = messageQueue.poll();
            if (null != message) {
                localMessageStore.stash(message);
            }
        }

        //Refresh local message store configuration file.
        if (null != localMessageStore) {
            localMessageStore.close();
            localMessageStore = null;
        }

        LOGGER.warn("BufferedMQProducer shuts down completely.");
    }

    AtomicLong getErrorSendingCounter() {
        return errorSendingCounter;
    }

    AtomicLong getSuccessSendingCounter() {
        return successSendingCounter;
    }

    public LocalMessageStore getLocalMessageStore() {
        return localMessageStore;
    }

    public Region getTargetRegion() {
        return targetRegion;
    }

    public void setTargetRegion(Region targetRegion) {
        this.targetRegion = targetRegion;
    }

    private class MessageSender implements Runnable {
        private boolean running = true;
        @Override
        public void run() {
            while (running) {
                Message message = null;
                try {
                    message = messageQueue.take();
                    producer.send(message, messageQueueSelector, new SendMessageCallback(BufferedMQProducer.this, sendCallback, message));
                } catch (Exception e) {
                    if (null != message) {
                        localMessageStore.stash(message);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Message stashed due to sending failure");
                        }
                    } else {
                        LOGGER.error("Message being null when exception raised.", e);
                    }
                }
            }
        }

        public void stop() {
            running = false;
        }
    }
}