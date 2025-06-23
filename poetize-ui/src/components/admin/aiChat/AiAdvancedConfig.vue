<template>
  <div class="ai-advanced-config">
    <el-form :model="advancedConfig" label-width="120px">
      <el-form-item label="代理设置">
        <el-input v-model="advancedConfig.proxy" placeholder="例如: http://proxy.example.com:8080"></el-input>
      </el-form-item>

      <el-form-item label="超时时间(秒)">
        <el-input-number v-model="advancedConfig.timeout" :min="5" :max="300"></el-input-number>
      </el-form-item>

      <el-form-item label="重试次数">
        <el-input-number v-model="advancedConfig.retryCount" :min="0" :max="5"></el-input-number>
      </el-form-item>

      <el-form-item label="自定义Headers">
        <div v-for="(header, index) in advancedConfig.customHeaders" :key="index" class="header-item">
          <el-input v-model="header.key" placeholder="Header名称" style="width: 200px; margin-right: 10px;"></el-input>
          <el-input v-model="header.value" placeholder="Header值" style="width: 300px; margin-right: 10px;"></el-input>
          <el-button type="danger" icon="el-icon-delete" @click="removeHeader(index)"></el-button>
        </div>
        <el-button type="primary" icon="el-icon-plus" @click="addHeader">添加Header</el-button>
      </el-form-item>

      <el-form-item label="🧠 启用思考模式">
        <el-switch v-model="advancedConfig.enableThinking"></el-switch>
        <small class="help-text">启用后AI会先思考再回答，提供更深入的分析（仅部分模型支持，如o1系列）</small>
      </el-form-item>

      <el-form-item label="调试模式">
        <el-switch v-model="advancedConfig.debugMode"></el-switch>
        <small class="help-text">启用后会在控制台输出详细日志</small>
      </el-form-item>

      <el-form-item label="数据导出">
        <el-button @click="exportConfig">导出配置</el-button>
        <el-button @click="showImportDialog">导入配置</el-button>
      </el-form-item>
    </el-form>

    <!-- 导入配置对话框 -->
    <el-dialog title="导入配置" :visible.sync="importDialogVisible" width="500px">
      <el-upload
        drag
        :action="uploadUrl"
        :before-upload="beforeConfigUpload"
        :on-success="handleConfigImport"
        accept=".json">
        <i class="el-icon-upload"></i>
        <div class="el-upload__text">将配置文件拖到此处，或<em>点击上传</em></div>
        <div class="el-upload__tip" slot="tip">只能上传json格式的配置文件</div>
      </el-upload>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'AiAdvancedConfig',
  props: {
    value: {
      type: Object,
      default: () => ({
        proxy: '',
        timeout: 30,
        retryCount: 3,
        customHeaders: [],
        debugMode: false,
        enableThinking: false
      })
    }
  },
  
  data() {
    return {
      advancedConfig: { ...this.value },
      importDialogVisible: false
    }
  },
  
  computed: {
    uploadUrl() {
      return this.$constant.baseURL + "/admin/upload";
    }
  },
  
  watch: {
    value: {
      handler(newVal) {
        this.advancedConfig = { ...newVal };
      },
      deep: true
    },
    
    advancedConfig: {
      handler(newVal) {
        this.$emit('input', newVal);
      },
      deep: true
    }
  },
  
  methods: {
    // 添加自定义Header
    addHeader() {
      this.advancedConfig.customHeaders.push({ key: '', value: '' });
    },
    
    // 移除自定义Header
    removeHeader(index) {
      this.advancedConfig.customHeaders.splice(index, 1);
    },
    
    // 导出配置
    exportConfig() {
      // 触发父组件的导出事件
      this.$emit('export-config');
    },
    
    // 显示导入对话框
    showImportDialog() {
      this.importDialogVisible = true;
    },
    
    // 配置文件上传前验证
    beforeConfigUpload(file) {
      const isJson = file.type === 'application/json' || file.name.endsWith('.json');
      if (!isJson) {
        this.$message.error('只能上传JSON格式的配置文件!');
        return false;
      }
      return true;
    },
    
    // 处理配置导入
    handleConfigImport(res) {
      if (res.flag) {
        try {
          const config = JSON.parse(res.data);
          this.$emit('import-config', config);
          this.$message.success('配置导入成功');
          this.importDialogVisible = false;
        } catch (error) {
          this.$message.error('配置文件格式错误');
        }
      } else {
        this.$message.error('配置导入失败');
      }
    }
  }
}
</script>

<style scoped>
.header-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.help-text {
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
  margin-top: 5px;
  display: block;
}
</style> 