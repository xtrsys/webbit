package org.webbitserver.netty;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpCookie;
import java.nio.charset.Charset;

import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer;

public class NettyHttpResponse implements org.webbitserver.HttpResponse {

    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    private final ChannelHandlerContext ctx;
    private final HttpResponse response;
    private final Thread.UncaughtExceptionHandler exceptionHandler;
    private final Thread.UncaughtExceptionHandler ioExceptionHandler;
    private final ChannelBuffer responseBuffer;
    private Charset charset;

    public NettyHttpResponse(ChannelHandlerContext ctx,
                             HttpResponse response,
                             Thread.UncaughtExceptionHandler exceptionHandler,
                             Thread.UncaughtExceptionHandler ioExceptionHandler) {
        this.ctx = ctx;
        this.response = response;
        this.exceptionHandler = exceptionHandler;
        this.ioExceptionHandler = ioExceptionHandler;
        this.charset = DEFAULT_CHARSET;
        responseBuffer = ChannelBuffers.dynamicBuffer();
    }

    @Override
    public NettyHttpResponse charset(Charset charset) {
        this.charset = charset;
        return this;
    }

    @Override
    public Charset charset() {
        return charset;
    }

    @Override
    public NettyHttpResponse status(int status) {
        response.setStatus(HttpResponseStatus.valueOf(status));
        return this;
    }

    @Override
    public int status() {
        return response.getStatus().getCode();
    }

    @Override
    public NettyHttpResponse header(String name, String value) {
        if (value == null) {
            response.removeHeader(name);
        } else {
            response.addHeader(name, value);
        }
        return this;
    }

    @Override
    public NettyHttpResponse header(String name, long value) {
        response.addHeader(name, value);
        return this;
    }

    @Override
    public NettyHttpResponse cookie(HttpCookie httpCookie) {
        return header(SET_COOKIE_HEADER, httpCookie.toString());
    }

    @Override
    public NettyHttpResponse content(String content) {
        responseBuffer.writeBytes(copiedBuffer(content, charset()));
        return this;
    }

    @Override
    public NettyHttpResponse content(byte[] content) {
        responseBuffer.writeBytes(copiedBuffer(content));
        return this;
    }

    @Override
    public NettyHttpResponse write(String content) {
        write(copiedBuffer(content, CharsetUtil.UTF_8));
        return this;
    }

    @Override
    public NettyHttpResponse error(Throwable error) {
        response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        String message = getStackTrace(error);
        header("Content-Type", "text/plain");
        header("Content-Length", message.length());
        content(message);
        flushResponse();

        exceptionHandler.uncaughtException(Thread.currentThread(), error);

        return this;
    }

    private String getStackTrace(Throwable error) {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        error.printStackTrace(writer);
        writer.flush();
        return buffer.toString();
    }

    @Override
    public NettyHttpResponse end() {
        flushResponse();
        return this;
    }

    private void flushResponse() {
        // Send the response and close the connection.
        try {
            ChannelFuture future = write(responseBuffer);
            future.addListener(ChannelFutureListener.CLOSE);
        } catch (Exception e) {
            ioExceptionHandler.uncaughtException(Thread.currentThread(), e);
        }
    }

    private ChannelFuture write(ChannelBuffer responseBuffer) {
        response.setContent(responseBuffer);
        return ctx.getChannel().write(response);
    }

}
