/**
 * 加锁测试
 * join测试
 */

#include<pthread.h>
#include<stdio.h>

int b = 0;
int c = 1;

pthread_t t2;
pthread_mutex_t l1;

void *thread2(void *arg) {

	pthread_mutex_lock(&l1);
	b++;
	printf("%d, b = %d\n", (c++), b);
	pthread_mutex_unlock(&l1);

}

void main() {

	b = 1;

	printf("main:\n");

	int a = 0;
	while(a < 3) {
		if (a >= 0) {
			pthread_create(&t2, NULL, thread2, NULL);
			// pthread_join(t2, NULL);
		}
		a++;
	}
	pthread_wait(t2);

	return;
}
