package com.example.indoorlocation.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtil {

    /**
     * 将Uri转换为File对象
     * @param context 上下文
     * @param uri 图片Uri
     * @return 转换后的File对象，失败返回null
     */
    public static File uriToFile(Context context, Uri uri) {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        File tempFile = null;

        try {
            // 检查Uri和上下文是否有效
            if (uri == null || context == null) {
                return null;
            }

            // 获取输入流
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }

            // 创建临时文件
            String fileName = getFileName(context.getContentResolver(), uri);
            File cacheDir = context.getExternalCacheDir();

            // 如果外部缓存目录不可用，使用内部缓存目录
            if (cacheDir == null) {
                cacheDir = context.getCacheDir();
            }

            tempFile = new File(cacheDir, fileName);

            // 写入文件
            outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024 * 4]; // 4KB缓冲区
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();

            return tempFile;

        } catch (IOException e) {
            e.printStackTrace();
            // 发生异常时删除可能创建的不完整文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            return null;
        } finally {
            // 确保流被关闭
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 从Uri获取文件名
     * @param contentResolver ContentResolver
     * @param uri 文件Uri
     * @return 文件名
     */
    public static String getFileName(ContentResolver contentResolver, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result != null ? result : ("temp_file_" + System.currentTimeMillis());
    }

    /**
     * 获取文件从Uri
     * @param context 上下文
     * @param uri 图片Uri
     * @return 转换后的File对象，失败返回null
     */
    public static File getFileFromUri(Context context, Uri uri) {
        return uriToFile(context, uri);
    }
}
