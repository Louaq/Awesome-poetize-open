import ReconnectingWebSocket from 'reconnecting-websocket';

/**
 * @param {*} ws_protocol wss or ws
 * @param {*} ip
 * @param {*} port
 * @param {*} paramStr 加在ws url后面的请求参数，形如：name=张三&id=12
 * @param {*} binaryType 'blob' or 'arraybuffer'
 */
export default function (ws_protocol, ip, port, paramStr, binaryType) {

  this.ws_protocol = ws_protocol;
  this.ip = ip;
  this.port = port;
  this.paramStr = paramStr;
  this.binaryType = binaryType;
  this.connected = false;

  if (port === "") {
    this.url = ws_protocol + '://' + ip + '/socket';
  } else {
    this.url = ws_protocol + '://' + ip + ":" + port + '/socket';
  }
  if (paramStr) {
    this.url += '?' + paramStr;
  }

  this.connect = () => {
    try {
      let ws = new ReconnectingWebSocket(this.url, [], {
        maxRetries: 10,
        reconnectionDelayGrowFactor: 1.3,
        connectionTimeout: 4000
      });
      this.ws = ws;
      ws.binaryType = this.binaryType;

      ws.onopen = (event) => {
        console.log('WebSocket连接已建立');
        this.connected = true;
        //获取离线消息
      }

      ws.onclose = (event) => {
        console.log('WebSocket连接已关闭');
        this.connected = false;
      }

      ws.onerror = (event) => {
        console.error('WebSocket连接发生错误');
        this.connected = false;
      }
    } catch (error) {
      console.error('创建WebSocket连接失败', error);
      this.connected = false;
    }
  }

  this.send = (data) => {
    if (this.ws && this.connected) {
      try {
        this.ws.send(data);
        return true;
      } catch (error) {
        console.error('发送消息失败', error);
        return false;
      }
    } else {
      console.warn('WebSocket未连接，无法发送消息');
      return false;
    }
  }
}
