<template>
  <div class="ai-appearance-config">
    <el-form :model="appearanceConfig" label-width="120px">
      <el-form-item label="机器人头像">
        <div class="avatar-upload">
          <el-upload
            class="avatar-uploader"
            :action="uploadUrl"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
            :before-upload="beforeAvatarUpload">
            <img v-if="appearanceConfig.botAvatar" :src="appearanceConfig.botAvatar" class="avatar">
            <i v-else class="el-icon-plus avatar-uploader-icon"></i>
          </el-upload>
        </div>
      </el-form-item>

      <el-form-item label="机器人名称">
        <el-input v-model="appearanceConfig.botName" placeholder="例如: 小助手"></el-input>
      </el-form-item>

      <el-form-item label="主题颜色">
        <el-color-picker v-model="appearanceConfig.themeColor"></el-color-picker>
      </el-form-item>

      <el-form-item label="聊天窗口位置">
        <el-radio-group v-model="appearanceConfig.position">
          <el-radio label="bottom-right">右下角</el-radio>
          <el-radio label="bottom-left">左下角</el-radio>
          <el-radio label="center">居中</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="聊天气泡样式">
        <el-radio-group v-model="appearanceConfig.bubbleStyle">
          <el-radio label="modern">现代风格</el-radio>
          <el-radio label="classic">经典风格</el-radio>
          <el-radio label="minimal">简约风格</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="显示打字动效">
        <el-switch v-model="appearanceConfig.typingAnimation"></el-switch>
      </el-form-item>

      <el-form-item label="显示时间戳">
        <el-switch v-model="appearanceConfig.showTimestamp"></el-switch>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
export default {
  name: 'AiAppearanceConfig',
  props: {
    value: {
      type: Object,
      default: () => ({
        botAvatar: '',
        botName: 'AI助手',
        themeColor: '#409EFF',
        position: 'bottom-right',
        bubbleStyle: 'modern',
        typingAnimation: true,
        showTimestamp: true
      })
    }
  },
  
  data() {
    return {
      appearanceConfig: { ...this.value }
    }
  },
  
  computed: {
    uploadUrl() {
      return this.$constant.baseURL + "/admin/upload";
    },
    
    uploadHeaders() {
      return {
        'Authorization': 'Bearer ' + localStorage.getItem('adminToken')
      };
    }
  },
  
  watch: {
    value: {
      handler(newVal) {
        this.appearanceConfig = { ...newVal };
      },
      deep: true
    },
    
    appearanceConfig: {
      handler(newVal) {
        this.$emit('input', newVal);
      },
      deep: true
    }
  },
  
  methods: {
    // 头像上传成功
    handleAvatarSuccess(res) {
      if (res.flag) {
        this.appearanceConfig.botAvatar = res.data;
        this.$message.success('头像上传成功');
      } else {
        this.$message.error('头像上传失败');
      }
    },
    
    // 头像上传前验证
    beforeAvatarUpload(file) {
      const isImage = file.type.indexOf('image/') === 0;
      const isLt2M = file.size / 1024 / 1024 < 2;
      
      if (!isImage) {
        this.$message.error('只能上传图片文件!');
        return false;
      }
      if (!isLt2M) {
        this.$message.error('图片大小不能超过 2MB!');
        return false;
      }
      return true;
    }
  }
}
</script>

<style scoped>
.avatar-uploader .el-upload {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.avatar-uploader .el-upload:hover {
  border-color: #409EFF;
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 100px;
  height: 100px;
  line-height: 100px;
  text-align: center;
}

.avatar {
  width: 100px;
  height: 100px;
  display: block;
}
</style> 