import {useStore} from 'vuex';

import {useDialog} from 'naive-ui';

import {nextTick} from 'vue';

import {ElMessage} from "element-plus";

import {reactive, getCurrentInstance, onMounted, onBeforeUnmount, watchEffect, toRefs} from 'vue';

export default function () {
  const globalProperties = getCurrentInstance().appContext.config.globalProperties;
  const $common = globalProperties.$common;
  const $http = globalProperties.$http;
  const $constant = globalProperties.$constant;
  const store = useStore();
  const dialog = useDialog();

  let imUtilData = reactive({
    //系统消息
    systemMessages: [],
    showBodyLeft: true,
    //表情包
    imageList: [],
    // 存储事件监听器引用，避免重复绑定
    bodyRightClickHandler: null
  })

  onMounted(() => {
    if ($common.mobile()) {
      const friendAside = document.querySelector(".friend-aside");
      if (friendAside) {
        // 确保不重复绑定
        friendAside.removeEventListener('click', handleFriendAsideClick);
        friendAside.addEventListener('click', handleFriendAsideClick);
      }

      const bodyRight = document.querySelector(".body-right");
      if (bodyRight) {
        // 确保不重复绑定
        bodyRight.removeEventListener('click', handleInitialBodyRightClick);
        bodyRight.addEventListener('click', handleInitialBodyRightClick);
      }
    }
    mobileRight();
  })

  onBeforeUnmount(() => {
    if ($common.mobile()) {
      // 清理事件监听器
      const friendAside = document.querySelector(".friend-aside");
      if (friendAside) {
        friendAside.removeEventListener('click', handleFriendAsideClick);
      }

      const bodyRight = document.querySelector(".body-right");
      if (bodyRight) {
        bodyRight.removeEventListener('click', handleInitialBodyRightClick);
      }

      // 清理动态添加的事件监听器
      if (imUtilData.bodyRightClickHandler) {
        const bodyRightElements = document.querySelectorAll('.body-right');
        bodyRightElements.forEach(element => {
          element.removeEventListener('click', imUtilData.bodyRightClickHandler);
        });
      }
    }
  })

  function handleFriendAsideClick() {
    imUtilData.showBodyLeft = true;
    mobileRight();
  }

  function handleInitialBodyRightClick() {
    imUtilData.showBodyLeft = false;
    mobileRight();
  }

  function changeAside() {
    imUtilData.showBodyLeft = !imUtilData.showBodyLeft;
    mobileRight();
  }

  function mobileRight() {
    if ($common.mobile()) {
      const bodyLeft = document.querySelector(".body-left");
      const bodyRight = document.querySelector(".body-right");
      
      console.log(`[移动端] mobileRight 调用 - showBodyLeft: ${imUtilData.showBodyLeft}`);
      
      if (imUtilData.showBodyLeft) {
        // 显示左侧面板（聊天列表）
        if (bodyLeft) {
          bodyLeft.classList.remove("hidden");
          // 添加进入动画
          setTimeout(() => {
            bodyLeft.style.animation = 'slideInFromLeft 0.3s ease-out';
          }, 10);
        }
        if (bodyRight) {
          bodyRight.classList.remove("full-width", "mobile-right");
        }
        console.log('[移动端] 显示左侧面板（聊天列表）');
      } else {
        // 隐藏左侧面板，显示右侧内容
        if (bodyLeft) {
          bodyLeft.classList.add("hidden");
        }
        if (bodyRight) {
          bodyRight.classList.add("full-width");
          bodyRight.classList.remove("mobile-right");
          // 添加退出动画
          bodyRight.style.animation = 'slideInFromRight 0.3s ease-out';
        }
        console.log('[移动端] 隐藏左侧面板，显示右侧内容');
      }
      
      // 清除动画，避免重复触发
      setTimeout(() => {
        if (bodyLeft) bodyLeft.style.animation = '';
        if (bodyRight) bodyRight.style.animation = '';
      }, 300);
    }
  }

  // 确保侧边栏按钮状态正确更新
  function updateAsideActiveState(targetType) {
    // 清除所有侧边栏按钮的激活状态
    document.querySelectorAll('.friend-chat').forEach(btn => {
      btn.classList.remove('aside-active');
    });
    
    // 根据类型设置对应按钮为激活状态
    const buttons = document.querySelectorAll('.friend-chat');
    if (buttons.length >= 3) {
      if (targetType === 1) {
        buttons[0].classList.add('aside-active'); // 聊天按钮
      } else if (targetType === 2) {
        buttons[1].classList.add('aside-active'); // 好友按钮
      } else if (targetType === 3) {
        buttons[2].classList.add('aside-active'); // 群聊按钮
      }
    }
  }

  function getSystemMessages() {
    $http.get($constant.baseURL + "/imChatUserMessage/listSystemMessage")
      .then((res) => {
        if (!$common.isEmpty(res.data) && !$common.isEmpty(res.data.records)) {
          imUtilData.systemMessages = res.data.records;
        }
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }

  function hiddenBodyLeft() {
    if ($common.mobile()) {
      const bodyRightElements = document.querySelectorAll('.body-right');
      
      // 如果已经有处理器，先移除
      if (imUtilData.bodyRightClickHandler) {
        bodyRightElements.forEach(element => {
          element.removeEventListener('click', imUtilData.bodyRightClickHandler);
        });
      }
      
      // 创建新的处理器
      imUtilData.bodyRightClickHandler = function(event) {
        // 只有在显示左侧面板时才处理点击
        if (imUtilData.showBodyLeft) {
          imUtilData.showBodyLeft = false;
          mobileRight();
        }
      };
      
      // 添加事件监听器
      bodyRightElements.forEach(element => {
        element.addEventListener('click', imUtilData.bodyRightClickHandler);
      });
    }
  }

  function imgShow() {
    // 使用原生JavaScript替代jQuery
    const messageImages = document.querySelectorAll('.message img');
    messageImages.forEach(img => {
      // 移除之前的事件监听器，避免重复绑定
      img.removeEventListener('click', handleImageClick);
      // 添加新的事件监听器
      img.addEventListener('click', handleImageClick);
    });
  }
  
  function handleImageClick(event) {
    const src = event.target.getAttribute('src');
    const bigImg = document.getElementById('bigImg');
    if (bigImg) {
      bigImg.setAttribute('src', src);
    }

    // 获取当前点击图片的真实大小，并显示弹出层及大图
    const tempImg = new Image();
    tempImg.onload = function() {
      const windowW = window.innerWidth; // 获取当前窗口宽度
      const windowH = window.innerHeight; // 获取当前窗口高度
      const realWidth = this.width; // 获取图片真实宽度
      const realHeight = this.height; // 获取图片真实高度
      let imgWidth, imgHeight;
      const scale = 0.8; // 缩放尺寸

      if (realHeight > windowH * scale) {
        imgHeight = windowH * scale;
        imgWidth = imgHeight / realHeight * realWidth;
        if (imgWidth > windowW * scale) {
          imgWidth = windowW * scale;
        }
      } else if (realWidth > windowW * scale) {
        imgWidth = windowW * scale;
        imgHeight = imgWidth / realWidth * realHeight;
      } else {
        imgWidth = realWidth;
        imgHeight = realHeight;
      }

      if (bigImg) {
        bigImg.style.width = imgWidth + 'px';
      }

      const w = (windowW - imgWidth) / 2;
      const h = (windowH - imgHeight) / 2;
      const innerImg = document.getElementById('innerImg');
      if (innerImg) {
        innerImg.style.top = h + 'px';
        innerImg.style.left = w + 'px';
      }

      const outerImg = document.getElementById('outerImg');
      if (outerImg) {
        outerImg.style.display = 'block';
        outerImg.style.opacity = '1';
      }
    };
    tempImg.src = src;

    // 点击外层关闭图片预览
    const outerImg = document.getElementById('outerImg');
    if (outerImg) {
      outerImg.removeEventListener('click', handleOuterImgClick);
      outerImg.addEventListener('click', handleOuterImgClick);
    }
  }
  
  function handleOuterImgClick(event) {
    const outerImg = event.currentTarget;
    outerImg.style.display = 'none';
    outerImg.style.opacity = '0';
  }

  function getImageList() {
    $http.get($constant.baseURL + "/resource/getImageList")
      .then((res) => {
        if (!$common.isEmpty(res.data)) {
          imUtilData.imageList = res.data;
        }
      })
      .catch((error) => {
        ElMessage({
          message: error.message,
          type: 'error'
        });
      });
  }

  function parseMessage(content) {
    content = content.replace(/\n{2,}/g, '<div style="height: 12px"></div>');
    content = content.replace(/\n/g, '<br/>');
    content = $common.faceReg(content);
    content = $common.pictureReg(content);
    return content;
  }

  return {
    imUtilData,
    changeAside,
    mobileRight,
    getSystemMessages,
    hiddenBodyLeft,
    imgShow,
    getImageList,
    parseMessage,
    updateAsideActiveState
  }
}
