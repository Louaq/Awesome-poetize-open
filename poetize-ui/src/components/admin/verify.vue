<template>
  <div class="myCenter verify-container">
    <div class="verify-content">
      <div>
        <el-avatar :size="50" :src="$store.state.webInfo.avatar"></el-avatar>
      </div>
      <div>
        <el-input v-model="account">
          <template slot="prepend">账号</template>
        </el-input>
      </div>
      <div>
        <el-input v-model="password" type="password">
          <template slot="prepend">密码</template>
        </el-input>
      </div>
      <div>
        <proButton :info="'提交'"
                   @click.native="login()"
                   :before="$constant.before_color_2"
                   :after="$constant.after_color_2">
        </proButton>
      </div>
    </div>
  </div>
</template>

<script>
  const proButton = () => import( "../common/proButton");

  import { handleLoginRedirect } from '../../utils/tokenExpireHandler';

  export default {
    components: {
      proButton
    },
    data() {
      return {
        redirect: this.$route.query.redirect || '/welcome',
        account: "",
        password: ""
      }
    },
    computed: {},
    created() {

    },
    methods: {
      login() {
        if (this.$common.isEmpty(this.account) || this.$common.isEmpty(this.password)) {
          this.$message({
            message: "请输入账号或密码！",
            type: "error"
          });
          return;
        }

        let user = {
          account: this.account.trim(),
          password: this.$common.encrypt(this.password.trim()),
          isAdmin: true
        };

        this.$http.post(this.$constant.baseURL + "/user/login", user, true, false)
          .then((res) => {
            if (!this.$common.isEmpty(res.data)) {
              // 清除旧的缓存数据
              localStorage.removeItem("currentAdmin");
              localStorage.removeItem("currentUser");

              // 设置新的token
              localStorage.setItem("userToken", res.data.accessToken);
              localStorage.setItem("adminToken", res.data.accessToken);

              // 更新Store状态
              this.$store.commit("loadCurrentUser", res.data);
              this.$store.commit("loadCurrentAdmin", res.data);

              this.account = "";
              this.password = "";

              // 显示登录成功消息
              if (this.$route.query.expired === 'true') {
                this.$message.success('重新登录成功');
              } else {
                this.$message.success('登录成功');
              }

              // 使用统一的重定向处理逻辑
              console.log('🔍 管理员登录成功，准备重定向，当前路由信息:', {
                path: this.$route.path,
                fullPath: this.$route.fullPath,
                query: this.$route.query
              });
              handleLoginRedirect(this.$route, this.$router, {
                defaultPath: '/welcome'
              });
            }
          })
          .catch((error) => {
            this.$message({
              message: error.message,
              type: "error"
            });
          });
      }
    }
  }
</script>

<style scoped>

  .verify-container {
    height: 100vh;
    background: var(--backgroundPicture) center center / cover repeat;
  }

  .verify-content {
    background: var(--maxWhiteMask);
    padding: 30px 40px 5px;
    position: relative;
  }

  .verify-content > div:first-child {
    position: absolute;
    left: 50%;
    transform: translate(-50%);
    top: -25px;
  }

  .verify-content > div:not(:first-child) {
    margin: 25px 0;
  }

  .verify-content > div:last-child > div {
    margin: 0 auto;
  }

</style>
