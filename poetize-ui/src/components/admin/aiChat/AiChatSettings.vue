<template>
  <div class="ai-chat-settings">
    <el-form :model="chatConfig" label-width="120px">
      <el-form-item label="系统提示词">
        <el-input 
          v-model="chatConfig.systemPrompt" 
          type="textarea" 
          :rows="4"
          placeholder="请输入AI的角色设定和行为指导">
        </el-input>
        <small class="help-text">定义AI的角色和回复风格</small>
      </el-form-item>

      <el-form-item label="欢迎消息">
        <el-input 
          v-model="chatConfig.welcomeMessage" 
          type="textarea" 
          :rows="2"
          placeholder="AI首次对话时的欢迎消息">
        </el-input>
      </el-form-item>

      <el-form-item label="对话历史数">
        <el-input-number 
          v-model="chatConfig.historyCount" 
          :min="0" 
          :max="20">
        </el-input-number>
        <small class="help-text">保留多少条历史对话用于上下文理解</small>
      </el-form-item>

      <el-form-item label="速率限制">
        <el-input-number 
          v-model="chatConfig.rateLimit" 
          :min="1" 
          :max="100"
          :precision="0">
        </el-input-number>
        <small class="help-text">每分钟最多允许的消息数量</small>
      </el-form-item>

      <el-form-item label="最大消息长度">
        <el-input-number 
          v-model="chatConfig.maxMessageLength" 
          :min="100" 
          :max="2000"
          :precision="0">
        </el-input-number>
        <small class="help-text">用户单条消息的最大字符数限制</small>
      </el-form-item>

      <el-form-item label="需要登录">
        <el-switch v-model="chatConfig.requireLogin"></el-switch>
        <small class="help-text">是否需要用户登录后才能使用AI聊天</small>
      </el-form-item>

      <el-form-item label="保存聊天记录">
        <el-switch v-model="chatConfig.saveHistory"></el-switch>
        <small class="help-text">是否保存用户的聊天历史记录</small>
      </el-form-item>

      <el-form-item label="内容过滤">
        <el-switch v-model="chatConfig.contentFilter"></el-switch>
        <small class="help-text">启用内容安全过滤</small>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
export default {
  name: 'AiChatSettings',
  props: {
    value: {
      type: Object,
      default: () => ({
        systemPrompt: "你是一个友善的AI助手，请用中文回答问题。",
        welcomeMessage: "你好！有什么可以帮助你的吗？",
        historyCount: 10,
        rateLimit: 20,
        requireLogin: false,
        saveHistory: true,
        contentFilter: true,
        maxMessageLength: 500
      })
    }
  },
  
  data() {
    return {
      chatConfig: { ...this.value }
    }
  },
  
  watch: {
    value: {
      handler(newVal) {
        this.chatConfig = { ...newVal };
      },
      deep: true
    },
    
    chatConfig: {
      handler(newVal) {
        this.$emit('input', newVal);
      },
      deep: true
    }
  }
}
</script>

<style scoped>
.help-text {
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
  margin-top: 5px;
  display: block;
}
</style> 