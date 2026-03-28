$(function () {
    var option = {
        url: '../sys/channel/list',
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
            {title: '通道名称', field: 'channelname'},
            {title: '通道类型', field: 'channeltype', formatter: function (v, r, i) {
                    if (v == 0) {
                        return "全网";
                    } else if (v == 1) {
                        return "移动";
                    } else if (v == 2) {
                        return "联通";
                    } else{
                        return "电信";
                    }
                }
            },
            {title: '通道地区', field: 'channelarea'},
            {title: '地区号段', field: 'channelareacode'},
            {title: '成本(厘/条)', field: 'channelprice'},
            {title: '账户接入号', field: 'spnumber'},
            {title: '通道IP', field: 'channelip'},
            {title: '通道端口', field: 'channelport'},
            {title: '通道账号', field: 'channelusername'},
            {title: '协议类型', field: 'protocaltype', formatter: function (v, r, i) {
                    if (v == 1) {
                        return "cmpp";
                    } else if (v == 2) {
                        return "sgip";
                    } else {
                        return "smgp";
                    }
                }
            },
            {title: '启用状态', field: 'isavailable', formatter: function (v) {
                    return String(v) === '1' ? '启用' : '停用';
                }
            }
        ]
    };
    $('#table').bootstrapTable(option);
});

function isIpv4(ip) {
    if (!ip) {
        return true;
    }
    var reg = /^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/;
    return reg.test(ip);
}

function isPositiveInteger(value) {
    if (value === null || value === undefined || value === '') {
        return false;
    }
    return /^\d+$/.test(String(value)) && parseInt(value, 10) > 0;
}

var vm = new Vue({
    el: '#dtapp',
    data: {
        showList: true,
        title: null,
        channel: {}
    },
    methods: {
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
                    url: "/sys/channel/del",
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
            vm.channel = {
                isavailable: 0
            };
        },
        update: function (event) {
            var id = 'id';
            var id = getSelectedRow()[id];
            if (id == null) {
                return;
            }

            $.get("../sys/channel/info/" + id, function (r) {
                vm.showList = false;
                vm.title = "修改";
                vm.channel = (r && r.data) ? (r.data.channel || r.data) : {};
                if (vm.channel.isavailable === null || vm.channel.isavailable === undefined || vm.channel.isavailable === '') {
                    vm.channel.isavailable = 0;
                }
            });
        },
        saveOrUpdate: function (event) {
            if (!vm.channel.channelname || $.trim(vm.channel.channelname) === '') {
                layer.alert("通道名称不能为空");
                return;
            }
            if (vm.channel.channeltype === null || vm.channel.channeltype === undefined || vm.channel.channeltype === '') {
                layer.alert("请选择通道类型");
                return;
            }
            if (!vm.channel.channelarea || $.trim(vm.channel.channelarea) === '') {
                layer.alert("通道地区不能为空");
                return;
            }
            if (vm.channel.channelprice === null || vm.channel.channelprice === undefined || vm.channel.channelprice === '' || isNaN(vm.channel.channelprice)) {
                layer.alert("通道成本不能为空且必须为数字");
                return;
            }
            if (Number(vm.channel.channelprice) < 0) {
                layer.alert("通道成本不能小于0");
                return;
            }
            if (vm.channel.protocaltype === null || vm.channel.protocaltype === undefined || vm.channel.protocaltype === '') {
                layer.alert("请选择协议类型");
                return;
            }
            if (vm.channel.channelport !== null && vm.channel.channelport !== undefined && vm.channel.channelport !== '' && !isPositiveInteger(vm.channel.channelport)) {
                layer.alert("通道端口必须为正整数");
                return;
            }
            if (!isIpv4(vm.channel.channelip)) {
                layer.alert("通道IP格式不正确");
                return;
            }

            vm.channel.channeltype = parseInt(vm.channel.channeltype, 10);
            vm.channel.protocaltype = parseInt(vm.channel.protocaltype, 10);
            vm.channel.channelprice = parseInt(vm.channel.channelprice, 10);
            if (vm.channel.channelport !== null && vm.channel.channelport !== undefined && vm.channel.channelport !== '') {
                vm.channel.channelport = parseInt(vm.channel.channelport, 10);
            }
            if (vm.channel.isavailable === null || vm.channel.isavailable === undefined || vm.channel.isavailable === '') {
                vm.channel.isavailable = 0;
            }
            vm.channel.isavailable = parseInt(vm.channel.isavailable, 10);

            var url = vm.channel.id == null ? "../sys/channel/save" : "../sys/channel/update";
            $.ajax({
                type: "POST",
                url: url,
                data: JSON.stringify(vm.channel),
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
