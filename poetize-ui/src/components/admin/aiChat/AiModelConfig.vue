<template>
  <div class="ai-model-config">
    <el-form :model="modelConfig" label-width="120px">
      <el-form-item label="AI服务商">
        <el-select v-model="modelConfig.provider" placeholder="请选择AI服务商" @change="onProviderChange">
          <el-option label="OpenAI" value="openai"></el-option>
          <el-option label="Claude (Anthropic)" value="anthropic"></el-option>
          <el-option label="Google Gemini" value="google"></el-option>
          <el-option label="百度文心" value="baidu"></el-option>
          <el-option label="阿里通义千问" value="alibaba"></el-option>
          <el-option label="腾讯混元" value="tencent"></el-option>
          <el-option label="自定义API" value="custom"></el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="API密钥">
        <el-input 
          v-model="modelConfig.apiKey" 
          type="password" 
          show-password
          placeholder="请输入API密钥"
          @input="onApiKeyInput">
        </el-input>
        <div v-if="isApiKeyMasked" class="api-key-status">
          <i class="el-icon-success"></i>
          <span>密钥已保存（出于安全考虑部分隐藏）</span>
          <el-button type="text" size="small" @click="showFullApiKey" v-if="!showingFullKey">重新输入密钥</el-button>
        </div>
        <div v-else class="help-text" style="margin-top: 5px;">
          API密钥保存后会自动隐藏敏感信息，这是正常的安全保护措施
        </div>
      </el-form-item>

      <el-form-item label="模型名称">
        <el-select 
          v-model="modelConfig.model" 
          :placeholder="modelConfig.provider === 'custom' ? '请输入自定义模型名称' : '请选择模型'" 
          filterable 
          allow-create
          :class="{'custom-model-select': modelConfig.provider === 'custom'}">
          <el-option 
            v-for="model in availableModels" 
            :key="model.value" 
            :label="model.label" 
            :value="model.value">
          </el-option>
        </el-select>
        <small class="help-text" v-if="modelConfig.provider === 'custom'">
          自定义API：请输入您的模型名称，支持任何兼容OpenAI格式的模型
        </small>
        <small class="help-text" v-else>
          根据所选服务商自动显示可用模型
        </small>
        <small class="help-text thinking-hint" v-if="isThinkingModelSelected">
          此模型支持思考模式，可在高级设置中启用以获得更深入的分析
        </small>
      </el-form-item>

      <el-form-item label="API基础URL" v-if="modelConfig.provider === 'custom'">
        <el-input 
          v-model="modelConfig.baseUrl" 
          placeholder="例如: https://api.example.com/v1">
        </el-input>
      </el-form-item>

      <el-form-item label="温度参数">
        <el-slider 
          v-model="modelConfig.temperature" 
          :min="0" 
          :max="2" 
          :step="0.1"
          show-tooltip>
        </el-slider>
        <small class="help-text">控制回复的随机性，0表示最确定，2表示最随机</small>
      </el-form-item>

      <el-form-item label="最大令牌数">
        <el-input-number 
          v-model="modelConfig.maxTokens" 
          :min="100" 
          :max="8000" 
          :step="100">
        </el-input-number>
        <small class="help-text">单次回复的最大长度</small>
      </el-form-item>

      <el-form-item label="启用AI聊天">
        <el-switch v-model="modelConfig.enabled"></el-switch>
      </el-form-item>

      <el-form-item label="启用流式响应">
        <el-switch v-model="modelConfig.enableStreaming"></el-switch>
        <small class="help-text">启用后AI回复将实时显示，提供更流畅的对话体验，包括工具调用过程可视化</small>
      </el-form-item>

      <el-form-item label="连接测试">
        <el-button @click="testConnection" :loading="testing">测试连接</el-button>
        <span v-if="isApiKeyMasked" class="help-text" style="margin-left: 10px;">
          将使用已保存的配置进行测试
        </span>
        <span v-else class="help-text" style="margin-left: 10px;">
          将使用当前输入的配置进行测试
        </span>
        <span v-if="testResult" :class="testResult.success ? 'test-success' : 'test-error'">
          {{ testResult.message }}
        </span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
export default {
  name: 'AiModelConfig',
  props: {
    value: {
      type: Object,
      default: () => ({
        provider: 'openai',
        apiKey: '',
        model: 'gpt-3.5-turbo',
        baseUrl: '',
        temperature: 0.7,
        maxTokens: 1000,
        enabled: false,
        enableStreaming: false
      })
    }
  },
  
  data() {
    return {
      modelConfig: { ...this.value },
      testing: false,
      testResult: null,
      isApiKeyMasked: true,
      showingFullKey: false,
      originalMaskedKey: ''
    }
  },
  
  computed: {
    availableModels() {
      const modelMap = {
        openai: [
          { label: 'GPT-4', value: 'gpt-4' },
          { label: 'GPT-4 Turbo', value: 'gpt-4-turbo-preview' },
          { label: 'GPT-4o', value: 'gpt-4o' },
          { label: 'o1-preview (思考模式)', value: 'o1-preview' },
          { label: 'o1-mini (思考模式)', value: 'o1-mini' },
          { label: 'GPT-3.5 Turbo', value: 'gpt-3.5-turbo' },
          { label: 'GPT-3.5 Turbo 16K', value: 'gpt-3.5-turbo-16k' }
        ],
        anthropic: [
          { label: 'Claude-3 Opus', value: 'claude-3-opus-20240229' },
          { label: 'Claude-3 Sonnet', value: 'claude-3-sonnet-20240229' },
          { label: 'Claude-3 Haiku', value: 'claude-3-haiku-20240307' }
        ],
        google: [
          { label: 'Gemini Pro', value: 'gemini-pro' },
          { label: 'Gemini Pro Vision', value: 'gemini-pro-vision' }
        ],
        baidu: [
          { label: '文心一言', value: 'ernie-bot' },
          { label: '文心一言 Turbo', value: 'ernie-bot-turbo' }
        ],
        alibaba: [
          { label: '通义千问', value: 'qwen-turbo' },
          { label: '通义千问 Plus', value: 'qwen-plus' }
        ],
        tencent: [
          { label: '混元大模型', value: 'hunyuan' }
        ],
        custom: [
          { label: 'GPT-3.5 Turbo (OpenAI兼容)', value: 'gpt-3.5-turbo' },
          { label: 'GPT-4 (OpenAI兼容)', value: 'gpt-4' },
          { label: 'GPT-4o (OpenAI兼容)', value: 'gpt-4o' },
          { label: 'o1-preview (思考模式)', value: 'o1-preview' },
          { label: 'o1-mini (思考模式)', value: 'o1-mini' },
          { label: 'Claude-3 Sonnet (兼容)', value: 'claude-3-sonnet-20240229' },
          { label: 'DeepSeek Chat', value: 'deepseek-chat' },
          { label: 'DeepSeek Coder', value: 'deepseek-coder' },
          { label: 'Qwen2.5-72B-Instruct (ModelScope)', value: 'Qwen/Qwen2.5-72B-Instruct' },
          { label: 'Qwen2.5-32B-Instruct (ModelScope)', value: 'Qwen/Qwen2.5-32B-Instruct' },
          { label: 'Moonshot v1', value: 'moonshot-v1-8k' },
          { label: 'GLM-4', value: 'glm-4' },
          { label: 'Qwen Turbo', value: 'qwen-turbo' },
          { label: '自定义模型', value: 'custom-model' }
        ]
      };
      return modelMap[this.modelConfig.provider] || [];
    },
    
    isThinkingModelSelected() {
      const thinkingModels = ['o1-preview', 'o1-mini'];
      return thinkingModels.includes(this.modelConfig.model) || 
             this.modelConfig.model.includes('o1') ||
             this.modelConfig.model.includes('thinking');
    }
  },
  
  watch: {
    value: {
      handler(newVal) {
        this.modelConfig = { ...newVal };
        this.isApiKeyMasked = this.modelConfig.apiKey && this.modelConfig.apiKey.includes('*');
        this.originalMaskedKey = this.isApiKeyMasked ? this.modelConfig.apiKey : '';
      },
      deep: true
    },
    
    modelConfig: {
      handler(newVal) {
        this.$emit('input', newVal);
      },
      deep: true
    }
  },
  
  methods: {
    onProviderChange() {
      const models = this.availableModels;
      if (models.length > 0) {
        if (this.modelConfig.provider === 'custom' && this.modelConfig.model) {
          // 保持现有模型名称不变
        } else {
          this.modelConfig.model = models[0].value;
        }
      } else if (this.modelConfig.provider === 'custom') {
        if (!this.modelConfig.model) {
          this.modelConfig.model = 'gpt-3.5-turbo';
        }
      }
      this.testResult = null;
    },
    
    async testConnection() {
      this.testing = true;
      this.testResult = '';

      try {
        if (this.isApiKeyMasked || (this.modelConfig.apiKey && this.modelConfig.apiKey.includes('*'))) {
          const response = await this.$http.post(this.$constant.pythonBaseURL + '/python/ai/chat/testConnection', {
            provider: this.modelConfig.provider,
            api_base: this.modelConfig.baseUrl,
            model: this.modelConfig.model,
            use_saved_config: true
          }, true);

          if (response.flag) {
            this.testResult = {
              success: true,
              message: response.message || '连接测试成功（使用已保存的配置）'
            };
            this.$message.success('连接测试成功（使用已保存的配置）');
          } else {
            this.testResult = {
              success: false,
              message: response.message || '连接测试失败'
            };
            this.$message.error('连接测试失败: ' + response.message);
          }
        } else {
          const testData = {
            provider: this.modelConfig.provider,
            api_key: this.modelConfig.apiKey,
            api_base: this.modelConfig.baseUrl,
            model: this.modelConfig.model
          };

          const response = await this.$http.post(this.$constant.pythonBaseURL + '/python/ai/chat/testConnection', testData, true);

          if (response.flag) {
            this.testResult = {
              success: true,
              message: response.message || '连接测试成功'
            };
            this.$message.success('连接测试成功');
          } else {
            this.testResult = {
              success: false,
              message: response.message || '连接测试失败'
            };
            this.$message.error('连接测试失败: ' + response.message);
          }
        }
      } catch (error) {
        this.testResult = {
          success: false,
          message: error.message
        };
        this.$message.error('连接测试失败: ' + error.message);
      } finally {
        this.testing = false;
      }
    },
    
    onApiKeyInput() {
      if (this.modelConfig.apiKey && !this.modelConfig.apiKey.includes('*')) {
        this.isApiKeyMasked = false;
        this.showingFullKey = false;
      }
      if (!this.modelConfig.apiKey) {
        this.isApiKeyMasked = false;
        this.showingFullKey = false;
      }
    },

    async showFullApiKey() {
      this.$confirm('要重新输入API密钥吗？当前密钥将被清空。', '重新输入密钥', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }).then(() => {
        this.isApiKeyMasked = false;
        this.showingFullKey = false;
        this.modelConfig.apiKey = '';
        this.$message.info('请重新输入您的API密钥');
      }).catch(() => {
        // 用户取消操作
      });
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
}

.api-key-status {
  margin-top: 5px;
  color: #67c23a;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.thinking-hint {
  color: #e6a23c;
}

.test-success {
  color: #67c23a;
  margin-left: 10px;
  font-size: 12px;
}

.test-error {
  color: #f56c6c;
  margin-left: 10px;
  font-size: 12px;
}

.custom-model-select {
  width: 100%;
}
</style> 