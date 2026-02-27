$(function () {
    vm.init();
});

var vm = new Vue({
    el: '#dtapp',
    data: {
        title: '短信发送',
        sites: [],
        sms: {
            clientId: '',
            mobile: '',
            content: '',
            state: 1
        }
    },
    methods: {
        init: function () {
            this.loadSites();
        },
        loadSites: function () {
            $.get('../sys/clientbusiness/all', function (r) {
                if (!r || r.code !== 0) {
                    layer.alert(r && r.msg ? r.msg : '加载客户列表失败');
                    return;
                }
                vm.sites = r.sites || [];
                if (!vm.sms.clientId && vm.sites.length > 0) {
                    vm.sms.clientId = vm.sites[0].id;
                }
            });
        },
        saveOrUpdate: function () {
            if (!vm.sms.clientId) {
                layer.alert('请选择客户');
                return;
            }
            if (!vm.sms.mobile || !vm.sms.mobile.trim()) {
                layer.alert('手机号不能为空');
                return;
            }
            if (!vm.sms.content || !vm.sms.content.trim()) {
                layer.alert('短信内容不能为空');
                return;
            }

            $.ajax({
                type: 'POST',
                url: '../sys/sms/save',
                data: JSON.stringify(vm.sms),
                success: function (r) {
                    if (!r) {
                        layer.alert('接口返回为空');
                        return;
                    }

                    var summary = r.data || {};
                    var msg = r.msg || '请求已完成';
                    if (summary.total != null) {
                        msg += '\n总数: ' + summary.total + '，成功: ' + summary.success + '，失败: ' + summary.failed;
                    }

                    if (r.code === 0) {
                        layer.alert(msg);
                    } else {
                        layer.alert(msg);
                    }
                },
                error: function () {
                    layer.alert('请求失败，请稍后重试');
                }
            });
        }
    }
});

