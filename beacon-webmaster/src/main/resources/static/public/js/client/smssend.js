$(function () {
    vm.init();
});

var vm = new Vue({
    el: '#dtapp',
    data: {
        title: 'SMS Send',
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
                    layer.alert(r && r.msg ? r.msg : 'load clients failed');
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
                layer.alert('client is required');
                return;
            }
            if (!vm.sms.mobile || !vm.sms.mobile.trim()) {
                layer.alert('mobile is required');
                return;
            }
            if (!vm.sms.content || !vm.sms.content.trim()) {
                layer.alert('content is required');
                return;
            }

            $.ajax({
                type: 'POST',
                url: '../sys/sms/save',
                data: JSON.stringify(vm.sms),
                success: function (r) {
                    if (!r) {
                        layer.alert('empty response');
                        return;
                    }

                    var summary = r.data || {};
                    var msg = r.msg || 'request finished';
                    if (summary.total != null) {
                        msg += '\nTotal: ' + summary.total + ', Success: ' + summary.success + ', Failed: ' + summary.failed;
                    }

                    if (r.code === 0) {
                        layer.alert(msg);
                    } else {
                        layer.alert(msg);
                    }
                },
                error: function () {
                    layer.alert('request failed');
                }
            });
        }
    }
});
