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

  if (port === "") {
    this.url = ws_protocol + '://' + ip + '/socket';
  } else {
    this.url = ws_protocol + '://' + ip + ":" + port + '/socket';
  }
  if (paramStr) {
    this.url += '?' + paramStr;
  }

  this.connect = () => {
    let ws = new ReconnectingWebSocket(this.url, [], {
      connectionTimeout: 4000,
      maxRetries: 10,
      reconnectInterval: 3000,
      maxReconnectInterval: 30000,
      reconnectDecay: 1.5,
      timeoutInterval: 2000,
      maxReconnectAttempts: 10,
      debug: false
    });
    this.ws = ws;
    ws.binaryType = this.binaryType;

    // 连接状态标记
    this.isConnected = false;
    this.isConnecting = false;

    ws.onopen = (event) => {
      console.log('WebSocket连接已建立');
      this.isConnected = true;
      this.isConnecting = false;
      
      // 触发自定义onopen事件
      if (this.onopen) {
        this.onopen(event);
      }
    }

    ws.onclose = (event) => {
      console.log('WebSocket连接已关闭', event.code, event.reason);
      this.isConnected = false;
      this.isConnecting = false;
      
      // 触发自定义onclose事件
      if (this.onclose) {
        this.onclose(event);
      }
    }

    ws.onerror = (event) => {
      console.error('WebSocket连接错误:', event);
      this.isConnected = false;
      
      // 触发自定义onerror事件
      if (this.onerror) {
        this.onerror(event);
      }
    }

    ws.onmessage = (event) => {
      // 触发自定义onmessage事件
      if (this.onmessage) {
        this.onmessage(event);
      }
    }
  }

  this.send = (data) => {
    if (!this.ws) {
      console.error('WebSocket未初始化');
      return false;
    }

    if (this.ws.readyState === WebSocket.CONNECTING) {
      console.warn('WebSocket正在连接中，请稍后重试');
      return false;
    }

    if (this.ws.readyState === WebSocket.OPEN) {
      try {
        this.ws.send(data);
        console.log('消息发送成功:', data);
        return true;
      } catch (error) {
        console.error('发送消息时出错:', error);
        return false;
      }
    } else {
      console.error('WebSocket连接未就绪，当前状态:', this.ws.readyState);
      return false;
    }
  }

  // 获取连接状态
  this.getReadyState = () => {
    return this.ws ? this.ws.readyState : WebSocket.CLOSED;
  }

  // 手动关闭连接
  this.close = () => {
    if (this.ws) {
      this.ws.close();
    }
  }

  // 检查连接是否可用
  this.isReady = () => {
    return this.ws && this.ws.readyState === WebSocket.OPEN;
  }
}
