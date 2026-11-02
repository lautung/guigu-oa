package com.lautung.oa.notice.image;

/** 图片读取结果：字节 + 存储的 content-type（读端点据此写响应头）。 */
public record NoticeImageContent(byte[] content, String contentType) {
}
