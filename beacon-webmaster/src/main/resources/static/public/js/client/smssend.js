$(function () {
    vm.init();
});

var vm = new Vue({
    el: '#dtapp',
    data: {
        title: '短信发送工作台',
        sites: [],
        sending: false,
        sms: {
            clientId: '',
            mobile: '',
            content: '',
            state: 1
        },
        resultSummary: {
            total: 0,
            success: 0,
            failed: 0,
            message: ''
        }
    },
    computed: {
        mobileCount: function () {
            return this.parseMobileList(this.sms.mobile).length;
        },
        contentLength: function () {
            return this.sms.content ? this.sms.content.trim().length : 0;
        }
    },
    methods: {
        init: function () {
            this.loadSites();
        },
        loadSites: function () {
            $.get('../sys/client-business/all', function (r) {
                if (!r || r.code !== 0) {
                    layer.alert(r && r.msg ? r.msg : '加载客户列表失败');
                    return;
                }
                vm.sites = r.data || [];
                if (!vm.sms.clientId && vm.sites.length > 0) {
                    vm.sms.clientId = vm.sites[0].id;
                }
            });
        },
        resetForm: function () {
            if (this.sending) {
                return;
            }
            this.sms.mobile = '';
            this.sms.content = '';
            this.sms.state = 1;
            this.resultSummary = {
                total: 0,
                success: 0,
                failed: 0,
                message: ''
            };
            if (this.sites.length > 0) {
                this.sms.clientId = this.sites[0].id;
            }
        },
        parseMobileList: function (rawMobiles) {
            if (!rawMobiles) {
                return [];
            }
            var tokens = rawMobiles.replace(/\r/g, '\n').split(/[,;\s]+/);
            var map = {};
            var result = [];
            for (var i = 0; i < tokens.length; i++) {
                var value = $.trim(tokens[i]);
                if (!value || map[value]) {
                    continue;
                }
                map[value] = true;
                result.push(value);
            }
            return result;
        },
        saveOrUpdate: function () {
            if (this.sending) {
                return;
            }
            if (!this.sms.clientId) {
                layer.alert('请选择客户');
                return;
            }
            if (!this.sms.mobile || !this.sms.mobile.trim()) {
                layer.alert('手机号不能为空');
                return;
            }
            if (!this.sms.content || !this.sms.content.trim()) {
                layer.alert('短信内容不能为空');
                return;
            }

            this.sending = true;
            $.ajax({
                type: 'POST',
                url: '../sys/sms/save',
                data: JSON.stringify(this.sms),
                success: function (r) {
                    if (!r) {
                        vm.resultSummary = {
                            total: vm.mobileCount,
                            success: 0,
                            failed: vm.mobileCount,
                            message: '接口返回为空'
                        };
                        layer.alert('接口返回为空');
                        return;
                    }

                    var summary = r.data || {};
                    vm.resultSummary = {
                        total: summary.total != null ? summary.total : vm.mobileCount,
                        success: summary.success != null ? summary.success : 0,
                        failed: summary.failed != null ? summary.failed : 0,
                        message: r.msg || summary.message || '请求已完成'
                    };

                    var message = vm.resultSummary.message;
                    if (summary.total != null) {
                        message += '\n总数: ' + vm.resultSummary.total + '，成功: ' + vm.resultSummary.success + '，失败: ' + vm.resultSummary.failed;
                    }
                    layer.alert(message);
                },
                error: function () {
                    vm.resultSummary = {
                        total: vm.mobileCount,
                        success: 0,
                        failed: vm.mobileCount,
                        message: '请求失败，请稍后重试'
                    };
                    layer.alert('请求失败，请稍后重试');
                },
                complete: function () {
                    vm.sending = false;
                }
            });
        }
    }
});

