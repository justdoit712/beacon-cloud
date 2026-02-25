$(function () {
    var option = {
        url: '../sys/clientchannel/list',
        pagination: true,	//显示分页条
        sidePagination: 'server',//服务器端分页
        showRefresh: true,  //显示刷新按钮
        search: true,
        toolbar: '#toolbar',
        striped: true,     //设置为true会有隔行变色效果
        //idField: 'menuId',
        columns: [
            {
                field: 'id',
                title: '序号',
                width: 40,
                formatter: function (value, row, index) {
                    var pageSize = $('#table').bootstrapTable('getOptions').pageSize;
                    var pageNumber = $('#table').bootstrapTable('getOptions').pageNumber;
                    return pageSize * (pageNumber - 1) + index + 1;
                }
            },
            {checkbox: true},
            {title: 'id', field: 'id', sortable: true},
            {title: '客户名称', field: 'corpname'},
            {title: '扩展号', field: 'extendnumber'},
            {title: '每条价格(厘)', field: 'price'},
            {title: '通道名称', field: 'channelname'}
        ]
    };
    $('#table').bootstrapTable(option);
});
var vm = new Vue({
    el: '#dtapp',
    data: {
        showList: true,
        title: null,
        sites: [],
        channelsites: [],
        clientchannel: {}
    },
    methods: {
        loadSites: function () {
            $.get("../sys/clientbusiness/all", function (r) {
                if (r && r.code === 0 && $.isArray(r.sites)) {
                    vm.sites = r.sites;
                    if (vm.sites.length === 0) {
                        layer.alert("未查询到可用客户数据，请先维护客户信息");
                    }
                } else {
                    vm.sites = [];
                    layer.alert((r && r.msg) ? r.msg : "客户下拉数据加载失败");
                }
            }).fail(function () {
                vm.sites = [];
                layer.alert("客户下拉数据加载失败，请稍后重试");
            });
        },
        loadChannelSites: function () {
            $.get("../sys/channel/all", function (r) {
                if (r && r.code === 0 && $.isArray(r.channelsites)) {
                    vm.channelsites = r.channelsites;
                    if (vm.channelsites.length === 0) {
                        layer.alert("未查询到可用通道数据，请先维护通道信息");
                    }
                } else {
                    vm.channelsites = [];
                    layer.alert((r && r.msg) ? r.msg : "通道下拉数据加载失败");
                }
            }).fail(function () {
                vm.channelsites = [];
                layer.alert("通道下拉数据加载失败，请稍后重试");
            });
        },
        del: function () {
            var rows = getSelectedRows();
            if (rows == null) {
                return;
            }
            var id = 'id';
            //提示确认框
            layer.confirm('您确定要删除所选数据吗？', {
                btn: ['确定', '取消'] //可以无限个按钮
            }, function (index, layero) {
                var ids = new Array();
                //遍历所有选择的行数据，取每条数据对应的ID
                $.each(rows, function (i, row) {
                    ids[i] = row[id];
                });

                $.ajax({
                    type: "POST",
                    url: "/sys/clientchannel/del",
                    data: JSON.stringify(ids),
                    success: function (r) {
                        if (r.code === 0) {
                            layer.alert('删除成功');
                            $('#table').bootstrapTable('refresh');
                        } else {
                            layer.alert(r.msg);
                        }
                    },
                    error: function () {
                        layer.alert('服务器没有返回数据，可能服务器忙，请重试');
                    }
                });
            });
        },
        add: function () {
            vm.showList = false;
            vm.title = "新增";
            vm.clientchannel = {};
            vm.loadSites();
            vm.loadChannelSites();
        },
        update: function (event) {
            var id = 'id';
            var id = getSelectedRow()[id];
            if (id == null) {
                return;
            }

            $.get("../sys/clientchannel/info/" + id, function (r) {
                vm.showList = false;
                vm.title = "修改";
                vm.clientchannel = r.clientchannel;
            });

            vm.loadSites();
            vm.loadChannelSites();
        },
        saveOrUpdate: function (event) {
            var url = vm.clientchannel.id == null ? "../sys/clientchannel/save" : "../sys/clientchannel/update";
            $.ajax({
                type: "POST",
                url: url,
                data: JSON.stringify(vm.clientchannel),
                success: function (r) {
                    if (r.code === 0) {
                        layer.alert(r.msg, function (index) {
                            layer.close(index);
                            vm.reload();
                        });
                    } else {
                        layer.alert(r.msg);
                    }
                }
            });
        },
        reload: function (event) {
            vm.showList = true;
            $("#table").bootstrapTable('refresh');
        }
    }
});