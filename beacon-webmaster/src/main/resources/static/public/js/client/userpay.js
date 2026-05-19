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
        corpname: '',
        currentMoney: null,
        amount: '',
        amountError: '',
        submitting: false
    },
    computed: {
        currentMoneyDisplay: function () {
            if (this.currentMoney === null || this.currentMoney === undefined) {
                return '加载中...';
            }
            return (this.currentMoney / 1000.0) + ' 元（' + this.currentMoney + ' 厘）';
        }
    },
    created: function () {
        var id = getUrlParam('clientId');
        if (!id) {
            layer.alert('缺少客户参数，请从客户接入列表进入', function () {
                vm.goBack();
            });
            return;
        }
        this.clientId = id;
        this.loadClientInfo(id);
    },
    methods: {
        loadClientInfo: function (id) {
            $.get('../sys/client-business/info/' + id, function (r) {
                if (r.code === 0 && r.data) {
                    var info = r.data.clientbusiness || r.data;
                    vm.corpname = info.corpname || '';
                    vm.currentMoney = info.money != null ? info.money : 0;
                } else {
                    vm.corpname = '未知客户';
                    vm.currentMoney = 0;
                }
            });
        },
        validate: function () {
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
