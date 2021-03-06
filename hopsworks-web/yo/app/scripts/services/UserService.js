/*
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

'use strict';

angular.module('hopsWorksApp')
        .factory('UserService', ['$http', 'TransformRequest', function ($http, TransformRequest) {
            return {
              UpdateProfile: function (user) {
                return $http.post('/api/user/updateProfile', TransformRequest.jQueryStyle(user));
              },
              profile: function () {
                return $http.get('/api/user/profile');
              },
              changeLoginCredentials: function (newCredentials) {
                return $http.post('/api/user/changeLoginCredentials', TransformRequest.jQueryStyle(newCredentials));
              },
              allcards: function () {
                return $http.get('/api/user/allcards');
              },
              createProject: function (newProject) {
                return $http.post('/api/user/newProject', newProject);
              },
              getRole: function (projectId) {
                return $http.post('/api/user/getRole', "projectId=" + projectId);
              },
              changeTwoFactor: function (newCredentials) {
                return $http.post('/api/user/changeTwoFactor', TransformRequest.jQueryStyle(newCredentials));
              },
              getQR: function (pwd) {
                return $http.post('/api/user/getQRCode', "password=" + pwd);
              },
              addSshKey: function (sshKey) {
              //addSshKey: function (name, sshKey) {
                return $http({
                  method: 'post',
                  url: '/api/user/addSshKey',
                  headers: {'Content-Type': 'application/json'},
                  isArray: false,
                  data: sshKey
                });

                //return $http.post('/api/user/addSshKey', "name=" + name + "&sshKey=" + sshKey);
              },
              removeSshKey: function (name) {
                return $http.post('/api/user/removeSshKey', "name="+name);
              },
              getSshKeys: function () {
                return $http.get('/api/user/getSshKeys');
              }
            };
          }]);
