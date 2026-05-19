$(function () {
    var option = {
        url: '../sys/strategy-filter/list',
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
            {title: '接入用户名', field: 'usercode'},
            {title: '策略过滤器列表', field: 'filters'}
        ]
    };
    $('#table').bootstrapTable(option);
});

var vm = new Vue({
    el: '#dtapp',
    data: {
        showList: true,
        title: null,
        filter: {},
        filterOptions: []
    },
    methods: {
        update: function (event) {
            var id = 'id';
            var id = getSelectedRow()[id];
            if (id == null) {
                return;
            }

            $.get("../sys/strategy-filter/info/" + id, function (r) {
                vm.showList = false;
                vm.title = "修改";
                vm.filter = (r && r.data) ? r.data : {};
            });
        },
        saveOrUpdate: function (event) {
            $.ajax({
                type: "POST",
                url: "../sys/strategy-filter/update",
                data: JSON.stringify(vm.filter),
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

$.get("../sys/strategy-filter/filters/all", function (r) {
    vm.filterOptions = (r && r.data) ? r.data : [];
});
