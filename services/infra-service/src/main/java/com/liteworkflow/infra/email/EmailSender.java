package com.liteworkflow.infra.email;

public interface EmailSender {

    void send(String recipientAddress, RenderedEmail email);
}
