import Tiows from "./tiows";
import constant from "./constant";
import {ElMessage} from "element-plus";

export default function () {
  this.ws_protocol = constant.wsProtocol;
  this.ip = constant.imBaseURL;
  this.port = constant.wsPort;
  this.paramStr = 'Authorization=' + localStorage.getItem("userToken");
  this.binaryType = 'blob';

  this.initWs = () => {
    try {
      this.tio = new Tiows(this.ws_protocol, this.ip, this.port, this.paramStr, this.binaryType);
      this.tio.connect();
    } catch (error) {
      console.error("WebSocket连接初始化失败:", error);
      ElMessage({
        message: "聊天服务连接失败，请稍后重试",
        type: 'error'
      });
    }
  }

  this.sendMsg = (value) => {
    try {
      if (this.tio && this.tio.ws && this.tio.ws.readyState === 1) {
        this.tio.send(value);
        return true;
      } else {
        ElMessage({
          message: "发送失败，请重试！",
          type: 'error'
        });
        return false;
      }
    } catch (error) {
      console.error("发送消息失败:", error);
      ElMessage({
        message: "发送失败，网络连接异常",
        type: 'error'
      });
      return false;
    }
  }
}
