package cc.ivera.util;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;


public class HttpUtils {

    /**
     * 将通知参数转化为字符串
     *
     * @param request 请求
     * @return 请求体
     */
    public static String readData(HttpServletRequest request) {
        try (BufferedReader br = request.getReader()) {
            StringBuilder result = new StringBuilder();
            for (String line; (line = br.readLine()) != null; ) {
                if (result.length() > 0) {
                    result.append("\n");
                }
                result.append(line);
            }
            return result.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
