function getUrlParam(name) {
    var reg = new RegExp('(^|&)' + name + '=([^&]*)(&|$)');
    var r = window.location.search.substr(1).match(reg);
    if (r != null) return decodeURIComponent(r[2]);
    return null;
}

var vm = new Vue({
    el: '#dtapp',
    data: {
        clientId: '',
        clientIdError: '',
        corpname: '',
        currentMoney: null,
        clientVerified: false,
        looking: false,
        amount: '',
        amountError: '',
        submitting: false
    },
    computed: {
        currentMoneyDisplay: function () {
            if (!this.clientVerified) {
                return '请先查询客户';
            }
            if (this.currentMoney === null || this.currentMoney === undefined) {
                return '加载中...';
            }
            return (this.currentMoney / 1000.0) + ' 元（' + this.currentMoney + ' 厘）';
        }
    },
    created: function () {
        var id = getUrlParam('clientId');
        if (id) {
            this.clientId = id;
            this.lookupClient();
        }
    },
    methods: {
        lookupClient: function () {
            var id = $.trim(this.clientId);
            if (!id) {
                this.clientIdError = '请输入客户业务 ID';
                return;
            }
            if (!/^\d+$/.test(id)) {
                this.clientIdError = '客户 ID 必须为数字';
                return;
            }
            this.clientIdError = '';
            this.clientVerified = false;
            this.corpname = '';
            this.currentMoney = null;
            this.looking = true;

            $.get('../sys/client-business/info/' + id, function (r) {
                vm.looking = false;
                if (r.code === 0 && r.data) {
                    var info = r.data.clientbusiness || r.data;
                    if (!info.corpname && !info.id) {
                        vm.clientIdError = '该客户 ID 不存在，请核实后重试';
                        vm.clientVerified = false;
                        return;
                    }
                    vm.corpname = info.corpname || '';
                    vm.currentMoney = info.money != null ? info.money : 0;
                    vm.clientVerified = true;
                    vm.clientIdError = '';
                } else {
                    vm.clientIdError = (r.msg || '客户不存在') + '，请核实后重试';
                    vm.clientVerified = false;
                }
            }).fail(function () {
                vm.looking = false;
                vm.clientIdError = '查询失败，请检查网络后重试';
            });
        },
        validate: function () {
            if (!this.clientVerified) {
                this.clientIdError = '请先查询并确认客户信息';
                return false;
            }
            var val = parseInt(this.amount, 10);
            if (!this.amount || isNaN(val)) {
                this.amountError = '请输入充值金额';
                return false;
            }
            if (val <= 0) {
                this.amountError = '充值金额必须大于0';
                return false;
            }
            this.amountError = '';
            return true;
        },
        submitPay: function () {
            if (!this.validate()) return;
            var self = this;
            var val = parseInt(this.amount, 10);

            layer.confirm(
                '确认为【' + this.corpname + '】充值 ' + val + ' 厘（' + (val / 1000.0) + ' 元）？',
                {btn: ['确认', '取消']},
                function (confirmIndex) {
                    layer.close(confirmIndex);
                    self.submitting = true;

                    $.ajax({
                        type: 'GET',
                        url: '../sys/client-business/pay',
                        data: {jine: val, clientId: self.clientId},
                        dataType: 'json',
                        success: function (r) {
                            self.submitting = false;
                            if (r.code === 0) {
                                var data = r.data || {};
                                var newBalance = data.balance != null ? data.balance : (self.currentMoney + val);
                                self.currentMoney = newBalance;
                                self.amount = '';
                                layer.alert('充值成功！当前余额：' + (newBalance / 1000.0) + ' 元');
                            } else {
                                layer.alert(r.msg || '充值失败');
                            }
                        },
                        error: function () {
                            self.submitting = false;
                            layer.alert('服务器没有返回数据，可能服务器忙，请重试');
                        }
                    });
                }
            );
        },
        goBack: function () {
            window.location.href = 'clientbusiness.html';
        }
    }
});
